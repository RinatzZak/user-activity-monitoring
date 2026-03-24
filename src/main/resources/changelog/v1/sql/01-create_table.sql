-- Создание таблицы users
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(100) NOT NULL UNIQUE,
                       action VARCHAR(50) DEFAULT 'UNKNOWN',
                       is_blocked BOOLEAN DEFAULT FALSE,
                       blocked_at TIMESTAMP,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP
);

COMMENT ON TABLE users IS 'Таблица пользователей';
COMMENT ON COLUMN users.id IS 'Идентификатор пользователя';
COMMENT ON COLUMN users.name IS 'Имя пользователя';
COMMENT ON COLUMN users.email IS 'Email пользователя';
COMMENT ON COLUMN users.action IS 'Последнее действие';
COMMENT ON COLUMN users.is_blocked IS 'Заблокирован';
COMMENT ON COLUMN users.blocked_at IS 'Время блокировки';
COMMENT ON COLUMN users.created_at IS 'Дата создания';
COMMENT ON COLUMN users.updated_at IS 'Дата обновления';