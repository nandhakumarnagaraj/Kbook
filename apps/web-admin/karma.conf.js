module.exports = function (config) {
  config.set({
    // The Angular-cli karma builder ONLY wires its webpack bundle through the
    // '@angular-devkit/build-angular' framework; without the explicit plugins
    // list below Karma cannot resolve that framework name and either fails
    // with "No provider for framework:..." or (worse) serves nothing and
    // reports "Executed 0 of 0".
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
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
