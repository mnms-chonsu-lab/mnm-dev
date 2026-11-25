import os, sys, tempfile, textwrap, unittest
from datetime import datetime, timedelta, timezone
from unittest import mock
sys.path.insert(0, os.path.dirname(__file__))
import run_notify as rn


def script(body):
    f = tempfile.NamedTemporaryFile("w", suffix=".py", delete=False, encoding="utf-8")
    f.write(textwrap.dedent(body)); f.close(); return f.name


class T(unittest.TestCase):
    def setUp(self):
        self.sent = []
        self.p = mock.patch.object(rn, "send", side_effect=self.sent.append); self.p.start()

    def tearDown(self):
        self.p.stop()

    def test_message(self):
        t = datetime(2026, 11, 26, 5, 10, tzinfo=timezone(timedelta(hours=9)))
        self.assertEqual(rn.message("outbreak", 2012, t), "✅ [staging] 수집 성공: 가축 질병 발생 정보 2,012건 (05:10)")

    def test_success_notified(self):
        code = rn.main([script('print("적재 대상: 2012 건")\nprint("성공: outbreak 2012건")\n')])
        self.assertEqual(code, 0); self.assertEqual(len(self.sent), 1); self.assertIn("가축 질병 발생 정보 2,012건", self.sent[0])

    def test_requested_many(self):
        code = rn.main([script('print("성공: disinfection_facility 225건")\nprint("성공: farm_scale 641건")\n')])
        self.assertEqual(code, 0); self.assertEqual(len(self.sent), 2)

    def test_no_request_no_notify(self):
        self.assertEqual(rn.main([script('print("요청 없음")\n')]), 0); self.assertEqual(self.sent, [])

    def test_failure_passes_code_no_notify(self):
        code = rn.main([script('import sys\nprint("실패: MAFRA_API_KEY 없음")\nsys.exit(1)\n')])
        self.assertEqual(code, 1); self.assertEqual(self.sent, [])

    def test_send_without_url_is_noop(self):
        self.p.stop()
        with mock.patch.dict(os.environ, {"NOTIFY_WEBHOOK_URL": ""}), mock.patch("urllib.request.urlopen") as u:
            rn.send("x"); u.assert_not_called()
        self.p.start()

    def test_send_sets_user_agent(self):
        self.p.stop()
        with mock.patch.dict(os.environ, {"NOTIFY_WEBHOOK_URL": "https://hook.example/x"}), mock.patch("urllib.request.urlopen") as u:
            rn.send("hi"); req = u.call_args[0][0]
            self.assertTrue(req.get_header("User-agent").startswith("mnm-etl-notify/")); self.assertEqual(req.data, '{"content": "hi"}'.encode())
        self.p.start()


if __name__ == "__main__":
    unittest.main()
