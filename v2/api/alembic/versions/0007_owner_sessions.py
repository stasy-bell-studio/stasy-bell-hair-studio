"""Revocable sessions with opaque, rotated refresh tokens.

Revision ID: 0007_owner_sessions
Revises: 0006_notification_outbox
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0007_owner_sessions"
down_revision = "0006_notification_outbox"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "owner_sessions",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("owner_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("master_accounts.id", ondelete="CASCADE"), nullable=False),
        sa.Column("refresh_hash", sa.String(64), nullable=False, unique=True),
        sa.Column("previous_hash", sa.String(64)),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False,
                  server_default=sa.func.now()),
        sa.Column("last_used_at", sa.DateTime(timezone=True)),
    )
    op.create_index("ix_owner_sessions_owner_id", "owner_sessions", ["owner_id"])


def downgrade():
    op.drop_index("ix_owner_sessions_owner_id", table_name="owner_sessions")
    op.drop_table("owner_sessions")
