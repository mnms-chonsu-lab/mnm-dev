# docs/api

`openapi.yaml`이 API 명세의 기준입니다. 변경 시 mnm-ai(schemas 영향) / mnm-data(DB 영향) 담당자를 PR 리뷰어로 지정합니다.

- **담당**: @dooooing, @joobogyeong

| 파일 | 내용 | 담당 |
| --- | --- | --- |
| `openapi.yaml` | 사용자 API (농가 앱·접수자 웹·어드민·인증) | @joobogyeong |
| `ai-internal.yaml` | AI 내부 API (`services/api` → `mnm-ai`). 외부에 열지 않음 | @dooooing |
| `gen_ai_internal.py` | `mnm-ai/schemas/`로 `ai-internal.yaml`을 다시 만드는 스크립트 | @dooooing |

`ai-internal.yaml`은 자동 생성 파일입니다. 직접 고치지 말고 `mnm-ai/schemas/`가 바뀌면 아래처럼 다시 만듭니다.

```bash
pip install pyyaml
python docs/api/gen_ai_internal.py ../mnm-ai/schemas docs/api/ai-internal.yaml
```
