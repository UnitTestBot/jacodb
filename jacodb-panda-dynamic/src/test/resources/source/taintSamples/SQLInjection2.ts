class Connection {
    constructor(obj) {}
    connect(callback) {}
}

class app {
    static connect(path, callback) {}
    static listen(port, callback) {}
}

const port = 3000;

const connection = Connection({
    dbms: 'mysql',
    host: 'localhost',
    user: 'root',
    password: '',
    database: 'test_db'
});

connection.connect(err => {
    if (err) {
        console.error('Error connecting to database:', err);
        return;
    }
    console.log('Connected to database');
});

app.get('/user', (req, res) => {
    const user = req.getUser();

    const query = `SELECT * FROM users WHERE username = '${user}'`;
    connection.query(query, (err, results) => {
        if (err) {
            res.status(500).send('Database query error');
            return;
        }
        res.json(results);
    });
});


app.listen(port, () => {
    console.log(`Server is running on http://localhost:${port}`);
});
