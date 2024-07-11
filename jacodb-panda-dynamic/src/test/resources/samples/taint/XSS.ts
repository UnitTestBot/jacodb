// <!DOCTYPE html>
// <html>
// <head>
//     <title>XSS Example</title>
// </head>
// <body>
//     <h1>User Comments</h1>
//     <div id="comments"></div>
//
//     <script>
function getUserComment() {
    // In a real scenario, this could be an input from a user
    return "<script>alert('XSS Attack!');</script>";
}

function displayComment(comment) {
    const commentsDiv = document.getElementById("comments");
    commentsDiv.innerHTML += "<p>" + comment + "</p>";
}

function usage() {
    let userComment = getUserComment();
    displayComment(userComment);
}

usage()
//     </script>
// </body>
// </html>