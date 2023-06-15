CREATE TABLE IF NOT EXISTS wh_registration (
	id serial PRIMARY KEY,
	product_id INT NOT NULL,
	app_id INT NOT NULL,
	wh_receiver_url VARCHAR UNIQUE NOT NULL
);