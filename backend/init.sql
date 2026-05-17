SELECT 'CREATE DATABASE users_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'users_db')\gexec

SELECT 'CREATE DATABASE contracts_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'contracts_db')\gexec

SELECT 'CREATE DATABASE gamification_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'gamification_db')\gexec