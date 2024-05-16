function query(sqlQuery: string, callback: Function): void {
  // Simulated database query
  const error = null; // Assuming no error for simplicity
  const results = []; // Simulated empty result
  callback(error, results);
}

function getUserData(username: string, callback: Function): void {
  const queryRequest = `SELECT * FROM users WHERE username = '${username}';`;
  query(queryRequest, callback);
}

function getUserName() {
    return "'; DROP TABLE users; --";
}

function usage() {
    let username = getUserName();
    getUserData(username, (error: any, result: any) => {
      if (error) {
        console.error("Error:", error);
      } else {
        console.log("Result:", result);
      }
    });
}