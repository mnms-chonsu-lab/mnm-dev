"""스테이징 저장소 테스트 파일 정리 (#40). 기본은 목록만 보여 준다.

- 지우는 곳: mnm-raw-stg 의 field/ · audio/
- 지우지 않는 곳: demo/ (데모용 사진·음성) 와 그 밖의 모든 경로

    python reset_storage.py           # 지울 파일 수·용량만 (바뀌는 것 없음)
    python reset_storage.py --apply   # 실제로 지움

키는 환경변수 NCP_ACCESS_KEY / NCP_SECRET_KEY 로만 받는다.
"""
from __future__ import annotations

import os
import sys

import boto3

BUCKET = os.environ.get("STORAGE_BUCKET", "mnm-raw-stg")
ENDPOINT = os.environ.get("STORAGE_ENDPOINT", "https://kr.object.ncloudstorage.com")
DELETE_PREFIXES = ("field/", "audio/")


def main() -> int:
    apply = sys.argv[1:] == ["--apply"]
    if sys.argv[1:] not in ([], ["--apply"]):
        print(__doc__)
        return 2
    s3 = boto3.client("s3", endpoint_url=ENDPOINT, region_name="kr-standard",
                      aws_access_key_id=os.environ["NCP_ACCESS_KEY"],
                      aws_secret_access_key=os.environ["NCP_SECRET_KEY"])
    pages = s3.get_paginator("list_objects_v2")
    for prefix in DELETE_PREFIXES:
        keys, size = [], 0
        for page in pages.paginate(Bucket=BUCKET, Prefix=prefix):
            for o in page.get("Contents", []):
                keys.append(o["Key"])
                size += o["Size"]
        print(f"{prefix}: {len(keys)}개, {size / 1e6:.1f}MB")
        if apply:
            # 하나씩 지운다. 여러 개 한 번에 지우기(delete_objects)는 네이버 Object Storage가
            # Content-MD5 를 요구해서 boto3 기본 설정으로는 실패할 수 있다 (CORS 설정 때 같은 문제)
            for k in keys:
                s3.delete_object(Bucket=BUCKET, Key=k)
            print(f"  → 지움 {len(keys)}개")
    demo = sum(len(p.get("Contents", [])) for p in pages.paginate(Bucket=BUCKET, Prefix="demo/"))
    print(f"demo/: {demo}개 (지우지 않음)")
    if not apply:
        print("목록만 봤어요. 지우려면 --apply")
    return 0


if __name__ == "__main__":
    sys.exit(main())
