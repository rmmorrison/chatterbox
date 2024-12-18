const Sequelize = require("sequelize");

const models = {};
const sequelize = new Sequelize('chatterbox', '', '', {
    host: 'localhost',
    dialect: 'sqlite',
    logging: false,
    storage: 'database.sqlite',
});

module.exports = {
    sequelize: sequelize,
    models: {}
}