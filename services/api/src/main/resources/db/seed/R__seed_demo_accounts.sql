-- 시연용 계정 (기능명세서 REQ-DATA-06 중 로그인에 필요한 부분): 접수 기관 2곳, 농가 7곳 + 등록 시연용 농가 2곳, 접수자 3명, 운영자 2명
-- 이름·농장식별번호·휴대폰 번호·주소는 모두 가상이다. 휴대폰은 010-0000-xxxx 대역.
-- 이미 있는 행(같은 기관 이름 · 농장식별번호 · 아이디)은 건드리지 않는다. 바꾼 비밀번호가 다시 임시 비밀번호로 돌아가지 않게 하려는 것.
-- 접수자·운영자는 임시 비밀번호로 시작한다(must_change_pw = true, 첫 로그인 때 R-S06). 원문은 레포에 두지 않고 따로 전달한다.

INSERT INTO receiver_org (name, type, region_codes, inboxes)
SELECT v.name, v.type, v.region_codes::char(10)[], v.inboxes::jsonb
FROM (VALUES
  ('홍성축협', '축협', '{4480000000}',
   '[{"inbox_id": "hs-nh-bio", "type": "biosecurity", "name": "방역팀", "phone": null},
     {"inbox_id": "hs-nh-history", "type": "history", "name": "이력 담당", "phone": null}]'),
  ('홍성군청 축산과', '시군청', '{4480000000}',
   '[{"inbox_id": "hs-gun-livestock", "type": "biosecurity", "name": "축산과", "phone": null}]')
) AS v(name, type, region_codes, inboxes)
WHERE NOT EXISTS (SELECT 1 FROM receiver_org o WHERE o.name = v.name);

INSERT INTO users (role, name, login_id, password_hash, must_change_pw, org_id, inbox_id)
SELECT v.role, v.name, v.login_id, v.password_hash, TRUE,
       (SELECT o.org_id FROM receiver_org o WHERE o.name = v.org_name ORDER BY o.org_id LIMIT 1), v.inbox_id
FROM (VALUES
  ('receiver', '박서연', 'hs-bio',     '$2a$10$m1cyYMV8Kvt9mhFT0zyrv.5O.m5pG6d.gfcFdkg.OLWeukaSRxZci',     '홍성축협',        'hs-nh-bio'),
  ('receiver', '최민준', 'hs-history', '$2a$10$wbzSruD3XNwLzoOEG0x8OuOGo7wcvHppxCLkjA7B3bnuWAPTPBkQu', '홍성축협',        'hs-nh-history'),
  ('receiver', '정하늘', 'hs-gun',     '$2a$10$WAv/J77WkktWrg132JgWwOC5RzQg3jvfRfr8Lc8Wlt4XxLtrnvone',     '홍성군청 축산과', 'hs-gun-livestock'),
  ('admin',    '유민',   'yumin',      '$2a$10$tfm.whmRDYnm/nxKQ3SRj.Vi5NxUZCyPCwo6eBUTRus3j77rz4KsG',      NULL,              NULL),
  ('admin',    '인서',   'inseo',      '$2a$10$CY79LIyQWjD1ldV52yhV3uni.LYiBB7nSfVSj5Povtsjw6qwG2z8C',      NULL,              NULL)
) AS v(role, name, login_id, password_hash, org_name, inbox_id)
ON CONFLICT (login_id) DO NOTHING;

-- 농가 계정은 비밀번호 없이 농장식별번호 + 휴대폰 번호로 로그인한다. 담당 축협은 모두 홍성축협
WITH v(farm_no, owner_name, phone, farm_name, address, head_count) AS (VALUES
  ('448012', '김한우', '01000000937', '김한우 농장', '충남 홍성군 홍성읍', 42),
  ('499901', '이송아', '01000000001', '송아지목장',  '충남 홍성군 광천읍', 35),
  ('499902', '박누렁', '01000000002', '누렁이농장',  '충남 홍성군 홍북읍', 58),
  ('499903', '최들판', '01000000003', '들판한우',    '충남 홍성군 장곡면', 27),
  ('499904', '정새벽', '01000000004', '새벽목장',    '충남 홍성군 은하면', 64),
  ('499905', '강솔밭', '01000000005', '솔밭농장',    '충남 홍성군 결성면', 19),
  ('499906', '윤은하', '01000000006', '은하한우',    '충남 홍성군 서부면', 46),
  -- 등록 시연용 (#46): 소·동의·기록 없이 둔다. 시연에서 소 등록·동의·첫 기록을 처음부터 보여준다
  ('499907', '서시연', '01000000007', '시연농장 1',  '충남 홍성군 구항면', 30),
  ('499908', '문처음', '01000000008', '시연농장 2',  '충남 홍성군 갈산면', 25)
),
new_owner AS (
  INSERT INTO users (role, name, phone)
  SELECT 'farmer', v.owner_name, v.phone
  FROM v
  WHERE NOT EXISTS (SELECT 1 FROM farm_info f WHERE f.farm_no = v.farm_no)
  RETURNING users_id, phone
)
INSERT INTO farm_info (farm_no, owner_user_id, name, address, head_count, org_id)
SELECT v.farm_no, n.users_id, v.farm_name, v.address, v.head_count,
       (SELECT o.org_id FROM receiver_org o WHERE o.name = '홍성축협' ORDER BY o.org_id LIMIT 1)
FROM v
JOIN new_owner n ON n.phone = v.phone;
