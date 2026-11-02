"""Durable owner notification delivery, idempotent per booking confirmation.

Revision ID: 0006_notification_outbox
Revises: 0005_finance
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0006_notification_outbox"
down_revision = "0005_finance"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "owner_notifications",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("entry_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("calendar_entries.id", ondelete="RESTRICT"), nullable=False),
        sa.Column("event_type", sa.String(32), nullable=False),
        sa.Column("state", sa.String(16), nullable=False, server_default="pending"),
        sa.Column("attempts", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("next_attempt_at", sa.DateTime(timezone=True), nullable=False,
                  server_default=sa.func.now()),
        sa.Column("sent_at", sa.DateTime(timezone=True)),
        sa.Column("last_error_code", sa.String(80)),
        sa.Column("created_at", sa.DateTime(timezone=True),
                  server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("entry_id", "event_type", name="uq_owner_notification_once"),
        sa.CheckConstraint("state IN ('pending','sent','stopped')", name="valid_owner_notification_state"),
        sa.CheckConstraint("attempts >= 0", name="nonnegative_delivery_attempts"),
    )


def downgrade():
    op.drop_table("owner_notifications")
