INSERT INTO patients (
  name,
  cpf,
  birth_date,
  phone,
  email,
  address,
  status,
  registration_date,
  last_consultation_date,
  target_date,
  created_at,
  updated_at
)
SELECT
  CASE
    WHEN x <= 10 THEN CONCAT('Paciente ', LPAD(CAST(x AS VARCHAR), 2, '0'), ' - Prenatal 36+ + Chronic')
    WHEN x <= 20 THEN CONCAT('Paciente ', LPAD(CAST(x AS VARCHAR), 2, '0'), ' - Child + Chronic')
    WHEN x <= 30 THEN CONCAT('Paciente ', LPAD(CAST(x AS VARCHAR), 2, '0'), ' - Child')
    WHEN x <= 40 THEN CONCAT('Paciente ', LPAD(CAST(x AS VARCHAR), 2, '0'), ' - Prenatal 28-36')
    ELSE CONCAT('Paciente ', LPAD(CAST(x AS VARCHAR), 2, '0'), ' - Prenatal <28')
  END AS name,
  LPAD(CAST(x AS VARCHAR), 11, '0') AS cpf,
  DATEADD('YEAR', -(20 + MOD(x, 15)), CURRENT_DATE) AS birth_date,
  CONCAT('119', LPAD(CAST(10000000 + x AS VARCHAR), 8, '0')) AS phone,
  CONCAT('paciente', LPAD(CAST(x AS VARCHAR), 2, '0'), '@PRIORIZASUS.test') AS email,
  CONCAT('Rua Teste, ', CAST(x AS VARCHAR)) AS address,
  'ACTIVE' AS status,
  CURRENT_DATE AS registration_date,
  DATEADD('DAY', -(14 + MOD(x, 4)), CURRENT_DATE) AS last_consultation_date,
  CASE
    WHEN x <= 10 THEN DATEADD('DAY', -(21 + (x - 1)), CURRENT_DATE)
    WHEN x <= 20 THEN DATEADD('DAY', -(11 + (x - 11)), CURRENT_DATE)
    WHEN x <= 30 THEN DATEADD('DAY', -(x - 20), CURRENT_DATE)
    WHEN x <= 40 THEN DATEADD('DAY', -(x - 31), CURRENT_DATE)
    ELSE DATEADD('DAY', -(x - 41), CURRENT_DATE)
  END AS target_date,
  CURRENT_TIMESTAMP AS created_at,
  CURRENT_TIMESTAMP AS updated_at
FROM SYSTEM_RANGE(1, 50) AS r(x);
