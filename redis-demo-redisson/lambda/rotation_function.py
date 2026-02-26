"""
AWS Lambda function for Redis password rotation with Secrets Manager.

This Lambda function handles the rotation of Redis passwords in AWS ElastiCache
using AWS Secrets Manager's rotation framework.

Required environment variables:
- SECRET_ARN: The ARN of the secret to rotate
- REDIS_HOST: Redis cluster endpoint
- REDIS_PORT: Redis port (default: 6379)

Required IAM permissions:
- secretsmanager:GetSecretValue
- secretsmanager:PutSecretValue
- secretsmanager:UpdateSecretVersionStage
- elasticache:ModifyReplicationGroup (if using API-based password update)
"""

import boto3
import json
import logging
import secrets
import string
import os
from datetime import datetime
from typing import Dict, Any, Optional

# Configure logging
logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

# AWS clients
secretsmanager = boto3.client('secretsmanager')
elasticache = boto3.client('elasticache')

# Configuration
SECRET_ARN = os.environ.get('SECRET_ARN')
REDIS_HOST = os.environ.get('REDIS_HOST')
REDIS_PORT = int(os.environ.get('REDIS_PORT', 6379))


def lambda_handler(event: Dict[str, Any], context: Any) -> Dict[str, Any]:
    """
    Main Lambda handler for secret rotation.

    Args:
        event: The rotation event from Secrets Manager
        context: Lambda context

    Returns:
        Dict containing the result of the rotation step
    """
    logger.info(f"Rotation event received: {json.dumps(event, default=str)}")

    secret_arn = event['SecretId']
    token = event['ClientRequestToken']
    step = event['Step']

    # Validate inputs
    if not secret_arn or not token or not step:
        raise ValueError("Missing required parameters in rotation event")

    # Get secret metadata
    metadata = secretsmanager.describe_secret(SecretId=secret_arn)

    # Check if secret is already being rotated
    if 'RotationEnabled' in metadata and not metadata['RotationEnabled']:
        raise ValueError(f"Secret {secret_arn} is not configured for rotation")

    # Get current version
    current_version = get_current_version(metadata)
    logger.info(f"Current version: {current_version}")

    # Execute rotation step
    try:
        if step == 'createSecret':
            return create_secret(secret_arn, token, current_version)
        elif step == 'setSecret':
            return set_secret(secret_arn, token, current_version)
        elif step == 'testSecret':
            return test_secret(secret_arn, token)
        elif step == 'finishSecret':
            return finish_secret(secret_arn, token, current_version)
        else:
            raise ValueError(f"Invalid step parameter: {step}")
    except Exception as e:
        logger.error(f"Rotation step {step} failed: {str(e)}")
        raise


def get_current_version(metadata: Dict[str, Any]) -> Optional[str]:
    """
    Get the current version ID from secret metadata.

    Args:
        metadata: Secret metadata from describe_secret

    Returns:
        Current version ID or None
    """
    for version_id, stages in metadata.get('VersionIdsToStages', {}).items():
        if 'AWSCURRENT' in stages:
            return version_id
    return None


def create_secret(secret_arn: str, token: str, current_version: Optional[str]) -> Dict[str, Any]:
    """
    Generate a new password and store it as AWSPENDING.

    Args:
        secret_arn: The ARN of the secret
        token: The client request token
        current_version: Current version ID

    Returns:
        Dict containing result
    """
    logger.info(f"createSecret: Creating new pending version {token}")

    # Check if pending version already exists
    try:
        pending = secretsmanager.get_secret_value(
            SecretId=secret_arn,
            VersionId=token,
            VersionStage='AWSPENDING'
        )
        logger.info("createSecret: Pending version already exists")
        return {'status': 'success', 'message': 'Pending version already exists'}
    except secretsmanager.exceptions.ResourceNotFoundException:
        pass  # Expected - need to create new version

    # Get current secret
    current_secret = secretsmanager.get_secret_value(
        SecretId=secret_arn,
        VersionId=current_version,
        VersionStage='AWSCURRENT'
    )

    secret_data = json.loads(current_secret['SecretString'])

    # Generate new password
    new_password = generate_password(32)
    secret_data['password'] = new_password
    secret_data['lastRotated'] = datetime.utcnow().isoformat()

    # Store as AWSPENDING
    secretsmanager.put_secret_value(
        SecretId=secret_arn,
        ClientRequestToken=token,
        SecretString=json.dumps(secret_data),
        VersionStages=['AWSPENDING']
    )

    logger.info(f"createSecret: Created pending version {token}")
    return {'status': 'success', 'message': f'Created pending version {token}'}


def set_secret(secret_arn: str, token: str, current_version: str) -> Dict[str, Any]:
    """
    Update Redis with the new password.

    Args:
        secret_arn: The ARN of the secret
        token: The client request token
        current_version: Current version ID

    Returns:
        Dict containing result
    """
    logger.info(f"setSecret: Updating Redis with new password")

    # Get pending secret (new password)
    pending_secret = secretsmanager.get_secret_value(
        SecretId=secret_arn,
        VersionId=token,
        VersionStage='AWSPENDING'
    )
    pending_data = json.loads(pending_secret['SecretString'])

    # Get current secret (old password)
    current_secret = secretsmanager.get_secret_value(
        SecretId=secret_arn,
        VersionStage='AWSCURRENT'
    )
    current_data = json.loads(current_secret['SecretString'])

    # Try to update Redis password
    try:
        import redis

        # Connect with current password
        client = redis.Redis(
            host=pending_data.get('host', REDIS_HOST),
            port=pending_data.get('port', REDIS_PORT),
            password=current_data['password'],
            ssl=True,
            decode_responses=True
        )

        # Test connection
        client.ping()
        logger.info("setSecret: Connected to Redis with current password")

        # Set new password using ACL (Redis 6.0+)
        try:
            client.execute_command(
                f"ACL SETUSER {pending_data.get('username', 'default')} "
                f"on >{pending_data['password']} ~* +@all"
            )
            logger.info("setSecret: Updated password via ACL")
        except redis.exceptions.ResponseError as e:
            if 'unknown command' in str(e).lower():
                # Fallback to ElastiCache API for older Redis versions
                update_via_elasticache_api(pending_data['clusterName'], pending_data['password'])
            else:
                raise

        client.close()
        logger.info("setSecret: Successfully updated Redis password")

    except ImportError:
        logger.warning("Redis library not available, using ElastiCache API")
        update_via_elasticache_api(pending_data.get('clusterName'), pending_data['password'])
    except Exception as e:
        logger.error(f"setSecret: Failed to update Redis password: {str(e)}")
        raise

    return {'status': 'success', 'message': 'Updated Redis password'}


def update_via_elasticache_api(cluster_name: str, new_password: str) -> None:
    """
    Update Redis password via ElastiCache ModifyReplicationGroup API.

    Args:
        cluster_name: The replication group ID
        new_password: The new password
    """
    logger.info(f"Updating password via ElastiCache API for cluster: {cluster_name}")

    try:
        elasticache.modify_replication_group(
            ReplicationGroupId=cluster_name,
            AuthToken=new_password,
            AuthTokenUpdateStrategy='ROTATE'
        )
        logger.info("Successfully updated password via ElastiCache API")
    except Exception as e:
        logger.error(f"Failed to update via ElastiCache API: {str(e)}")
        raise


def test_secret(secret_arn: str, token: str) -> Dict[str, Any]:
    """
    Test the new password by connecting to Redis.

    Args:
        secret_arn: The ARN of the secret
        token: The client request token

    Returns:
        Dict containing result
    """
    logger.info(f"testSecret: Testing new password")

    # Get pending secret
    pending_secret = secretsmanager.get_secret_value(
        SecretId=secret_arn,
        VersionId=token,
        VersionStage='AWSPENDING'
    )
    secret_data = json.loads(pending_secret['SecretString'])

    try:
        import redis

        # Connect with new password
        client = redis.Redis(
            host=secret_data.get('host', REDIS_HOST),
            port=secret_data.get('port', REDIS_PORT),
            password=secret_data['password'],
            ssl=True,
            decode_responses=True
        )

        # Test connection with PING
        result = client.ping()
        client.close()

        if result:
            logger.info("testSecret: Connection test successful")
            return {'status': 'success', 'message': 'Password test successful'}
        else:
            raise Exception("PING returned unexpected result")

    except ImportError:
        logger.warning("Redis library not available, skipping connection test")
        return {'status': 'success', 'message': 'Skipped (no redis library)'}
    except Exception as e:
        logger.error(f"testSecret: Password test failed: {str(e)}")
        raise


def finish_secret(secret_arn: str, token: str, current_version: str) -> Dict[str, Any]:
    """
    Promote the pending version to current.

    Args:
        secret_arn: The ARN of the secret
        token: The client request token
        current_version: Current version ID

    Returns:
        Dict containing result
    """
    logger.info(f"finishSecret: Promoting version {token} to AWSCURRENT")

    # Check if already current
    metadata = secretsmanager.describe_secret(SecretId=secret_arn)
    for version_id, stages in metadata.get('VersionIdsToStages', {}).items():
        if version_id == token and 'AWSCURRENT' in stages:
            logger.info("finishSecret: Version is already AWSCURRENT")
            return {'status': 'success', 'message': 'Version already current'}

    # Move AWSCURRENT to AWSPREVIOUS and AWSPENDING to AWSCURRENT
    secretsmanager.update_secret_version_stage(
        SecretId=secret_arn,
        VersionStage='AWSCURRENT',
        MoveToVersionId=token,
        RemoveFromVersionId=current_version
    )

    logger.info(f"finishSecret: Successfully promoted version {token}")
    return {'status': 'success', 'message': f'Promoted version {token} to AWSCURRENT'}


def generate_password(length: int = 32) -> str:
    """
    Generate a secure random password.

    Args:
        length: Password length

    Returns:
        Generated password
    """
    # Use alphanumeric + safe special characters
    alphabet = string.ascii_letters + string.digits + '!@#$%^&*'
    password = ''.join(secrets.choice(alphabet) for _ in range(length))
    return password


# For local testing
if __name__ == '__main__':
    test_event = {
        'SecretId': 'prod/elasticache/redis/auth',
        'ClientRequestToken': 'test-token-123',
        'Step': 'createSecret'
    }
    result = lambda_handler(test_event, None)
    print(json.dumps(result, indent=2))
