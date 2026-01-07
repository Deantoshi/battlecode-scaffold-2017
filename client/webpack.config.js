// This is all james' fault
// Don't worry, he eventually repented and became an electrical engineer

var webpack = require('webpack');
var merge = require('webpack-merge');
var path = require('path');
var CopyWebpackPlugin = require('copy-webpack-plugin');

var conf = {
  context: path.resolve(__dirname, 'src'),
  entry: {
    app: './app.ts',
  },
  output: {
    path: path.resolve(__dirname, 'bc17'),
    publicPath: '/bc17/',
    filename: 'bundle.js'
  },
  resolve: {
    // add `.ts` as a resolvable extension.
    extensions: ['.ts', '.js', '.png', '.jpg']
  },
  module: {
    rules: [
      { test: /\.ts$/, loader: 'awesome-typescript-loader', options: { transpileOnly: true, reportFiles: [] } },
      { test: /\.(png|jpg)$/, loader: 'url-loader?limit=10000&name=[name]-[hash:base64:7].[ext]' },
      { test: /\.css$/, loader: "style-loader!css-loader" }
    ]
  }
};

module.exports = function(env) {
  env = env || {};

  if (env.dev) {
    // we're in dev
    conf = merge(conf, {
      devtool: 'source-map',
      plugins: [
        new webpack.HotModuleReplacementPlugin(),
        new webpack.LoaderOptionsPlugin({
          minimize: false,
          debug: true
        }),
      ],
      devServer: {
        setup: function(app) {
          var fs = require('fs');
          var path = require('path');
          var cp = require('child_process');
          
          var projectRoot = path.resolve(__dirname, '..');
          var sourcePath = path.join(projectRoot, 'src');
          var mapPath = path.join(projectRoot, 'maps');
          var isWin = /^win/.test(process.platform);
          var gradlew = isWin ? 'gradlew.bat' : './gradlew';

          var SERVER_MAPS = [
            "Lanes", "HouseDivided", "Maniple", "Shrubbery", "Waves", "Slant",
            "Blitzkrieg", "BugTrap", "Cramped", "Croquembouche", "Misaligned",
            "Bullseye", "Chevron", "CropCircles", "DeathStar", "LilMaze",
            "Alone", "Clusters", "Aligned", "LilForts", "GreatDekuTree",
            "MyFirstMap", "Ocean", "GreenHouse", "Turtle", "Interference",
            "TheOtherSide", "Barbell", "Chess", "Arena", "Caterpillar",
            "PeacefulEncounter", "LineOfFire", "Captive", "Shortcut", "PacMan",
            "Planets", "1337Tree", "ModernArt", "Whirligig", "PasscalsTriangles",
            "TreeFarm", "DarkSide", "FlappyTree", "SparseForest", "HiddenTunnel",
            "Oxygen", "PureImagination", "Boxed", "GiantForest", "Sprinkles",
            "DenseForest", "HedgeMaze", "shrine", "OMGTree", "Levels",
            "TicTacToe", "Hurdle", "MagicWood", "Defenseless", "Barrier",
            "Present", "Snowflake", "Enclosure", "Grass", "DigMeOut",
            "CrossFire", "Conga", "Fancy", "Standoff"
          ];

          function walk(dir, done) {
            var results = [];
            fs.readdir(dir, function(err, list) {
              if (err) return done(err);
              var pending = list.length;
              if (!pending) return done(null, results);
              list.forEach(function(file) {
                file = path.resolve(dir, file);
                fs.stat(file, function(err, stat) {
                  if (stat && stat.isDirectory()) {
                    walk(file, function(err, res) {
                      results = results.concat(res);
                      if (!--pending) done(null, results);
                    });
                  } else {
                    results.push(file);
                    if (!--pending) done(null, results);
                  }
                });
              });
            });
          }

          app.get('/api/players', function(req, res) {
            walk(sourcePath, function(err, files) {
              if (err) { res.status(500).send(err); return; }
              var players = files
                .filter(function(file) { 
                  return file.endsWith('RobotPlayer.java') || 
                         file.endsWith('RobotPlayer.kt') || 
                         file.endsWith('RobotPlayer.scala'); 
                })
                .map(function(file) {
                  var rel = path.relative(sourcePath, file);
                  return rel.replace(/[\\\/]RobotPlayer\.[^\\\/.]+$/, '')
                            .replace(/[\\\/]/g, '.');
                });
              res.json(players);
            });
          });

          app.get('/api/maps', function(req, res) {
             fs.readdir(mapPath, function(err, files) {
               var localMaps = [];
               if (!err && files) {
                 localMaps = files.filter(function(f) { return f.endsWith('.map17'); })
                                  .map(function(f) { return f.substring(0, f.length - 6); });
               }
               res.json(localMaps.concat(SERVER_MAPS));
             });
          });

          app.get('/api/run', function(req, res) {
            var teamA = req.query.teamA;
            var teamB = req.query.teamB;
            var maps = req.query.maps;
            
            console.log("Running match: " + teamA + " vs " + teamB + " on " + maps);

            res.writeHead(200, {
              'Content-Type': 'text/event-stream',
              'Cache-Control': 'no-cache',
              'Connection': 'keep-alive'
            });

            var child = cp.spawn(gradlew, [
              'run',
              '-PteamA=' + teamA,
              '-PteamB=' + teamB,
              '-Pmaps=' + maps
            ], { cwd: projectRoot });

            child.stdout.on('data', function(data) {
              var lines = data.toString().split('\n');
              lines.forEach(function(line) {
                 if(line) res.write('event: stdout\ndata: ' + JSON.stringify(line) + '\n\n');
              });
            });

            child.stderr.on('data', function(data) {
              var lines = data.toString().split('\n');
              lines.forEach(function(line) {
                 if(line) res.write('event: stderr\ndata: ' + JSON.stringify(line) + '\n\n');
              });
            });

            child.on('close', function(code) {
               res.write('event: exit\ndata: ' + code + '\n\n');
               res.end();
            });
          });
        }
      }
    });
  } else {
    // we're compiling for prod
    conf = merge(conf, {
      plugins: [
        new webpack.optimize.UglifyJsPlugin(),
        new webpack.LoaderOptionsPlugin({
          minimize: true,
          debug: false
        }),
      ]
    });
  }

  if (env.electron) {
    // we're compiling for electron
    conf = merge(conf, {
      target: 'electron-renderer',
      plugins: [
        new webpack.DefinePlugin({
          'process.env.ELECTRON': true
        })
      ],
      // electron will find './bc17/thing.ext' but won't find '/bc17/thing.ext'
      output: { publicPath: './bc17/' }
    });
  } else {
    // we're compiling for the browser
    conf = merge(conf, {
      plugins: [
        new webpack.DefinePlugin({
          'process.env.ELECTRON': false
        })
      ],
      externals: {
        'electron': 'var {}',
        'os': 'var {}',
        'fs': 'var {}',
        'child_process': 'var {}',
        'http': 'var {}'
      }
    });
  }

  return conf;
};
