-- Character seed data
INSERT INTO character (name, persona, arc_length_days, created_at, updated_at, created_by, updated_by, is_deleted) VALUES
('Soul', 'A warm but wounded personality', 30, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false);

-- PersonaFact seed data
INSERT INTO persona_fact (character_id, fact_key, category, tier, content, unlock_day_from, unlock_day_to, created_at, updated_at, created_by, updated_by, is_deleted) VALUES
(1, 'favorite_food', 'TASTE', 1, 'I actually hate mint choco', 1, 10, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false),
(1, 'childhood_wound', 'WOUND', 2, 'I felt alone as a child', 5, 20, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false),
(1, 'daily_habit', 'HABIT', 1, 'I have the habit of writing in my journal every night', 1, 30, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false),
(1, 'core_value', 'VALUE', 2, 'I cherish listening deeply to others', 8, 25, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false),
(1, 'deepest_secret', 'SECRET', 3, 'There is a secret I cannot tell anyone', 20, 30, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'system', 'system', false);
