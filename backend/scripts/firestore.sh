#!/bin/bash
BASE="http://localhost:8079/v1/projects/university-booking-b4f4a/databases/(default)/documents"
FMT='import json,sys; print(json.dumps(json.load(sys.stdin), ensure_ascii=False, indent=2))'

curl -s "$BASE/$1${2:+/$2}" | python3 -c "$FMT"
