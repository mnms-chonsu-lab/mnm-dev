"""감시 프로그램 테스트. 실행: python -m unittest infra/docker/monitor/test_monitor.py"""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
from monitor import Config, LogTail, Monitor  # noqa: E402

LINE = '1.2.3.4 [22/Nov/2026:15:00:00 +0900] "GET /api/farmer/x HTTP/1.1" {s} 10 platform=farmer rt=0.01 rid=abc\n'


class Clock:
    def __init__(self):
        self.t = 1000.0

    def __call__(self):
        return self.t


class MonitorTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.log = os.path.join(self.dir, "access.log")
        open(self.log, "w").close()
        self.clock = Clock()
        self.api = (200, {"status": "UP"})
        self.ai = (200, {"status": "ok", "calls_5m": {"calls": 0, "failures": 0}})
        self.sent = []
        cfg = Config(webhook_url="http://hook", log_path=self.log, cooldown_sec=1800)
        self.mon = Monitor(cfg, send=self.sent.append, fetch=self.fetch, clock=self.clock)
        self.mon.step()  # 첫 확인: 로그 파일 끝 위치를 잡음

    def fetch(self, url):
        return self.api if "actuator" in url else self.ai

    def write(self, *statuses):
        with open(self.log, "a") as f:
            for s in statuses:
                f.write(LINE.format(s=s))

    def test_quiet_when_healthy(self):
        self.write(*[200] * 20)
        self.assertEqual(self.mon.step(), [])
        self.assertEqual(self.sent, [])

    def test_ai_error_rate_alert_cooldown_and_resolve(self):
        self.ai = (200, {"status": "ok", "calls_5m": {"calls": 10, "failures": 1}})  # 10% > 5%
        self.assertEqual(self.mon.step(), ["ai_error_rate"])
        self.assertIn("AI 오류율 10% (최근 5분 1/10건", self.sent[-1])
        self.clock.t += 60
        self.assertEqual(self.mon.step(), [])  # 30분 안에는 다시 안 보냄
        self.clock.t += 1800
        self.assertEqual(self.mon.step(), ["ai_error_rate"])  # 30분 지나면 다시
        self.ai = (200, {"status": "ok", "calls_5m": {"calls": 20, "failures": 1}})  # 5% → 기준 이하
        self.assertEqual(self.mon.step(), ["ai_error_rate:resolved"])

    def test_ai_error_rate_needs_min_calls(self):
        self.ai = (200, {"status": "ok", "calls_5m": {"calls": 3, "failures": 3}})
        self.assertEqual(self.mon.step(), [])

    def test_5xx_rate_from_nginx_log(self):
        self.write(*([200] * 18 + [502, 500]))  # 10%
        self.assertEqual(self.mon.step(), ["http_5xx"])
        self.assertIn("서버 오류(5xx) 10% (최근 5분 2/20건", self.sent[-1])
        self.clock.t += 301  # 5분 지나면 빠짐
        self.assertEqual(self.mon.step(), ["http_5xx:resolved"])

    def test_old_log_lines_ignored_at_start(self):
        with open(self.log, "a") as f:
            f.write(LINE.format(s=500) * 50)
        mon = Monitor(Config(log_path=self.log), send=self.sent.append, fetch=self.fetch, clock=self.clock)
        self.assertEqual(mon.step(), [])

    def test_down_after_two_checks(self):
        self.api = (None, None)
        self.assertEqual(self.mon.step(), [])  # 1회는 넘어감
        self.assertEqual(self.mon.step(), ["api_down"])
        self.api = (200, {"status": "UP"})
        self.assertEqual(self.mon.step(), ["api_down:resolved"])

    def test_log_truncated_when_too_big(self):
        self.mon.tail.max_bytes = 500
        self.write(*[200] * 10)
        self.mon.step()
        self.assertEqual(os.path.getsize(self.log), 0)
        self.write(*[500] * 10)  # 비운 뒤에도 이어서 읽음
        self.mon.step()
        self.assertEqual(self.mon.tail.stats(), (20, 10))

    def test_no_webhook_only_logs(self):
        sent = []
        mon = Monitor(Config(log_path=self.log), send=sent.append, fetch=lambda u: (None, None), clock=self.clock)
        mon.step(); mon.step()
        self.assertEqual(sent, [])

    def test_webhook_failure_does_not_stop(self):
        def boom(text):
            raise ConnectionError("http://hook/secret")
        self.mon.send = boom
        self.ai = (200, {"status": "ok", "calls_5m": {"calls": 10, "failures": 5}})
        self.assertEqual(self.mon.step(), ["ai_error_rate"])

    def test_webhook_sets_user_agent(self):
        # 디스코드는 파이썬 기본 User-Agent 를 403 으로 막는다 (#38)
        import monitor as m
        seen = {}

        class Resp:
            def close(self):
                pass

        def fake_urlopen(req, timeout):
            seen["ua"] = req.get_header("User-agent")
            seen["body"] = req.data
            return Resp()

        orig = m.urllib.request.urlopen
        m.urllib.request.urlopen = fake_urlopen
        try:
            Monitor(Config(log_path=self.log, webhook_url="https://hook.example/x"), clock=self.clock)._send_webhook("hi")
        finally:
            m.urllib.request.urlopen = orig
        self.assertTrue(seen["ua"].startswith("mnm-monitor/"))
        self.assertEqual(seen["body"], b'{"content": "hi"}')


if __name__ == "__main__":
    unittest.main()
