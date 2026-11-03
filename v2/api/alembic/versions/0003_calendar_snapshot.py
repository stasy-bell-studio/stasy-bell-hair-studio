"""Snapshots of manual appointment duration and price.

Revision ID: 0003_calendar_snapshot
Revises: 0002_master_account
"""
from alembic import op
import sqlalchemy as sa

revision = "0003_calendar_snapshot"
down_revision = "0002_master_account"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("calendar_entries", sa.Column("duration_minutes", sa.Integer()))
    op.add_column("calendar_entries", sa.Column("quoted_price_kopecks", sa.Integer()))
    op.create_check_constraint("valid_visit_duration", "calendar_entries",
                               "duration_minutes IS NULL OR duration_minutes > 0")
    op.create_check_constraint("valid_quoted_price", "calendar_entries",
                               "quoted_price_kopecks IS NULL OR quoted_price_kopecks >= 0")


def downgrade() -> None:
    op.drop_constraint("valid_quoted_price", "calendar_entries", type_="check")
    op.drop_constraint("valid_visit_duration", "calendar_entries", type_="check")
    op.drop_column("calendar_entries", "quoted_price_kopecks")
    op.drop_column("calendar_entries", "duration_minutes")
