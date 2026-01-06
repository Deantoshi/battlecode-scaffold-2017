// This file is a dirty hack that is necessary because nobody expected anyone
// to make an app that runs both in the browser and electron.

// To add another electron-exclusive module, add it to this file,
// and add it to 'externals' for non-webpack in webpack.config.js

// if we're on the web, webpack externals return empty objects
// they will ERROR AT RUNTIME if used
// only use them if you've made sure that process.env.ELECTRON === true

// in electron, actually imports real modules
// in browser, webpack externals return empty objects
var _electron: any = {};
var _os: any = {};
var _fs: any = {};
var _path: any = {};
var _child_process: any = {};
var _http: any = {};

if (process.env.ELECTRON) {
  _electron = require('electron');
  _os = require('os');
  _fs = require('fs');
  _path = require('path');
  _child_process = require('child_process');
  _http = require('http');
}

export var electron = _electron;
export var os = _os;
export var fs = _fs;
export var path = _path;
export var child_process = _child_process;
export var http = _http;

export default {
  electron: _electron,
  os: _os,
  fs: _fs,
  path: _path,
  child_process: _child_process,
  http: _http
}
