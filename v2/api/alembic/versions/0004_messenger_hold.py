"""45 minute holds, 15 minute token lifecycle and rate-limit events.

Revision ID: 0004_messenger_hold
Revises: 0003_calendar_snapshot
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0004_messenger_hold"
down_revision = "0003_calendar_snapshot"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("clients", sa.Column("consent_at", sa.DateTime(timezone=True)))
    op.add_column("calendar_entries", sa.Column("hold_access_sha256", sa.String(64)))
    op.add_column("calendar_entries", sa.Column("token_count", sa.Integer(), nullable=False, server_default="0"))
    op.create_check_constraint("valid_token_count", "calendar_entries",
                               "token_count >= 0 AND token_count <= 3")
    op.add_column("confirmation_tokens", sa.Column("revoked_at", sa.DateTime(timezone=True)))
    op.create_table(
        "public_rate_events",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("identity_hash", sa.String(64), nullable=False),
        sa.Column("action", sa.String(32), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True),
                  nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_public_rate_events_identity_hash", "public_rate_events", ["identity_hash"])


def downgrade():
    op.drop_column("clients", "consent_at")
    op.drop_index("ix_public_rate_events_identity_hash", table_name="public_rate_events")
    op.drop_table("public_rate_events")
    op.drop_column("confirmation_tokens", "revoked_at")
    op.drop_constraint("valid_token_count", "calendar_entries", type_="check")
    op.drop_column("calendar_entries", "token_count")
    op.drop_column("calendar_entries", "hold_access_sha256")
