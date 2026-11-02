"""Local optional worker for expiry. Not deployed or enabled in CI/production."""
import logging
import signal
import time

from app.db import get_session_factory
from app.owner_notifications import process_owner_notifications
from app.scheduling import expire_holds, timeline_lock

logging.basicConfig(level=logging.INFO)
running = True


def stop(_signal, _frame):
    global running
    running = False


def run():
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    while running:
        try:
            with get_session_factory()() as db:
                timeline_lock(db)
                expire_holds(db)
                db.commit()
                process_owner_notifications(db)
        except Exception:
            logging.exception("Hold expiry cycle failed")
        for _ in range(30):
            if not running:
                return
            time.sleep(1)


if __name__ == "__main__":
    run()
