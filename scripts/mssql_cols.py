import os, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "api"))
from config import load_env, mssql_config
load_env()
import pymssql
cfg = mssql_config()
conn = pymssql.connect(**{k: cfg[k] for k in cfg}, autocommit=True)
cur = conn.cursor()
for t in ['BILL_DTLS','BILL_TEST_DTLS','APNT_HEAD','RECEIPT_HEAD']:
    cur.execute(f"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='{t}' ORDER BY ORDINAL_POSITION")
    print(t, [r[0] for r in cur.fetchall()])
conn.close()
