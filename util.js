const fs = require('fs');
const path = require('path');

exports.walkDirectoryTree = function walkDirectoryTree(directory, callback = () => {}) {
    const files = fs.readdirSync(directory);

    files.forEach((file) => {
        const filePath = path.join(directory, file);

        if (fs.statSync(filePath).isDirectory()) {
            walkDirectoryTree(filePath, callback);
        }

        callback(filePath);
    });
}