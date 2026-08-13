-- Character seed data
INSERT INTO character (name, persona, arc_length_days, created_at, updated_at, created_by, updated_by, is_deleted) VALUES
('소울', '따뜻하지만 상처를 숨기는 성격. 겉으론 웃어도 속엔 오래된 외로움이 있다.', 30, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false);

-- PersonaFact seed data
INSERT INTO persona_fact (character_id, fact_key, category, tier, content, unlock_day_from, unlock_day_to, created_at, updated_at, created_by, updated_by, is_deleted) VALUES
(1, 'favorite_food', 'TASTE', 1, '사실 민트초코를 싫어해', 1, 10, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false),
(1, 'childhood_wound', 'WOUND', 2, '어릴 때 늘 혼자였다는 기억이 있어', 5, 20, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false),
(1, 'daily_habit', 'HABIT', 1, '매일 밤 일기를 쓰는 습관이 있어', 1, 30, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false),
(1, 'core_value', 'VALUE', 2, '누군가의 이야기를 깊이 들어주는 걸 소중히 여겨', 8, 25, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false),
(1, 'deepest_secret', 'SECRET', 3, '누구에게도 말하지 못한 비밀이 하나 있어', 20, 30, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false);
