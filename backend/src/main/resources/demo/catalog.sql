-- Invented demonstration data. No university requirements or student records.
INSERT INTO subject (code, name) VALUES ('DEMO', 'Demonstration Computing') ON CONFLICT (code) DO NOTHING;
INSERT INTO course (subject_id, number, title, description, credits, normalized)
SELECT s.id, c.number, c.title, 'Invented course for the local portfolio demonstration.', 3, true
FROM subject s CROSS JOIN (VALUES ('101', 'Programming Foundations'), ('201', 'Data Structures'),
('210', 'Database Design'), ('301', 'Software Engineering'), ('310', 'Applied Statistics'), ('401', 'Team Project')) c(number, title)
WHERE s.code='DEMO' ON CONFLICT (subject_id, number) DO NOTHING;
INSERT INTO program (code, name, program_type, college, degree_type)
VALUES ('PORTFOLIO-DEMO', 'Demonstration Computing', 'MAJOR', 'Demo College', 'BS') ON CONFLICT (code) DO NOTHING;
INSERT INTO program_catalog_version (program_id, catalog_year, version_label)
SELECT id, 2025, '2025-2026' FROM program WHERE code='PORTFOLIO-DEMO' ON CONFLICT (program_id, catalog_year) DO NOTHING;
INSERT INTO bulletin_scrape_log (catalog_label, catalog_year, bulletin_root)
VALUES ('2025-2026', 2025, 'https://catalog.example.edu') ON CONFLICT (catalog_label) DO NOTHING;
INSERT INTO req_node (pcv_id, name, kind, order_index, n_required, notes)
SELECT v.id, 'Demonstration core: choose two courses', 'N_OF', 0, 2,
'Invented requirements for exploring the interface. Not an academic program.'
FROM program_catalog_version v JOIN program p ON p.id=v.program_id WHERE p.code='PORTFOLIO-DEMO'
AND NOT EXISTS (SELECT 1 FROM req_node n WHERE n.pcv_id=v.id);
INSERT INTO req_node_course_option (req_node_id, course_id)
SELECT n.id, c.id FROM req_node n JOIN program_catalog_version v ON v.id=n.pcv_id
JOIN program p ON p.id=v.program_id CROSS JOIN course c JOIN subject s ON s.id=c.subject_id
WHERE p.code='PORTFOLIO-DEMO' AND n.name='Demonstration core: choose two courses' AND s.code='DEMO'
ON CONFLICT DO NOTHING;

-- Simple invented prerequisites make scheduling warnings testable without a scrape.
INSERT INTO course_prerequisite_group (course_id, kind, operator, description)
SELECT c.id, 'PREREQUISITE', 'AND', 'Synthetic demo prerequisite'
FROM course c JOIN subject s ON s.id=c.subject_id
WHERE s.code='DEMO' AND c.number IN ('201','301')
AND NOT EXISTS (SELECT 1 FROM course_prerequisite_group g WHERE g.course_id=c.id AND g.description='Synthetic demo prerequisite');
INSERT INTO course_prerequisite_course (group_id, required_course_id)
SELECT g.id, prerequisite.id FROM course_prerequisite_group g
JOIN course target ON target.id=g.course_id JOIN subject s ON s.id=target.subject_id
JOIN course prerequisite ON prerequisite.subject_id=s.id
AND prerequisite.number=CASE target.number WHEN '201' THEN '101' WHEN '301' THEN '201' END
WHERE s.code='DEMO' AND g.description='Synthetic demo prerequisite'
AND NOT EXISTS (SELECT 1 FROM course_prerequisite_course edge WHERE edge.group_id=g.id AND edge.required_course_id=prerequisite.id);
