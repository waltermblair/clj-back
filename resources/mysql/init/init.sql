USE gback;

CREATE TABLE Guesses (
    state VARCHAR(255) NOT NULL,
    actual_percent_enrolled INT NOT NULL,
    guessed_percent_enrolled INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
