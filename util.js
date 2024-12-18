const logger = require('pino')();
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

exports.isQuote = function(str) {
    // Remove emojis and count them (if necessary, use regex for specific emoji ranges)
    const emojiRegex = /\p{Emoji_Presentation}/gu;
    const stringWithoutEmojis = str.replace(emojiRegex, "");

    // Check if string length without emojis is more than 5
    if (stringWithoutEmojis.length <= 5) {
        logger.debug(`String failed length check: ${str}`);
        return false;
    }

    // Check if string represents a currency or URL
    const currencyOrUrlRegex = /\$|€|\u00A3|https?:\/\//i;
    if (currencyOrUrlRegex.test(stringWithoutEmojis)) {
        logger.debug(`String failed currency/URL check: ${str}`);
        return false;
    }

    // Check if all alphabetical characters are uppercase
    const alphaRegex = /[a-z]/; // Look for lowercase letters
    if (alphaRegex.test(stringWithoutEmojis)) {
        logger.debug(`String failed uppercase check: ${str}`);
        return false;
    }

    // Count non-alphabetical characters
    const nonAlphaCount = (stringWithoutEmojis.match(/[^a-zA-Z]/g) || []).length;
    const totalLength = stringWithoutEmojis.length;

    if (nonAlphaCount / totalLength > 0.25) {
        logger.debug(`String failed non-alphabetical character ratio check: ${str}`);
        return false;
    }

    return true;
}