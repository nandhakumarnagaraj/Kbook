module.exports = function (config) {
  config.set({
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    browsers: ['ChromeNoSandbox'],
    customLaunchers: {
      ChromeNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu']
      }
    },
    client: {
      jasmine: {
        random: true,
        seed: 'khanabook-orders'
      }
    },
    reporters: ['progress', 'kjhtml'],
    singleRun: true
  });
};
