/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

var exec = cordova.require('cordova/exec'); // eslint-disable-line no-undef
var utils = require('cordova/utils');
var Position = require('./Position');
var PositionError = require('./PositionError');

var isPositiveNumber = function (v) {
    return typeof v === 'number' && v > 0;
};

// Native watchPosition method is called async after permissions prompt.
// So we use additional map and own ids to return watch id synchronously.
var pluginToNativeWatchMap = {};

var androidGeolocation = {
    getCurrentPosition: function (success, error, args) {
        var win = function () {
            var geo = cordova.require('cordova/modulemapper').getOriginalSymbol(window, 'navigator.geolocation'); // eslint-disable-line no-undef
            geo.getCurrentPosition(success, error, args);
        };
        var fail = function () {
            if (error) {
                error(new PositionError(PositionError.PERMISSION_DENIED, 'Illegal Access'));
            }
        };
        exec(win, fail, 'Geolocation', 'getPermission', []);
    },

    watchPosition: function (success, error, args) {
        var pluginWatchId = utils.createUUID();
        var usesFrequency = false;
        var frequency = -1;

        // Don't use the frequency option if its less than 1 second since that kind of defeats the purpose of it.
        if (args && isPositiveNumber(args.frequency) && args.frequency >= 1000) {
            usesFrequency = true;
            frequency = args.frequency;

            // Don't allow the max age to be less than the frequency since frequency gets the ultimate say.
            if (!isPositiveNumber(args.maximumAge) || args.maximumAge < frequency) {
                args.maximumAge = frequency;
            }
        }

        var win = function () {
            var nativeWatchOptions = { id: null, usesFrequency: usesFrequency };

            if (usesFrequency) {

                var successWrapper = function (json) {
                    json = json || {};
                    success(new Position(json.coords, json.timestamp));
                };

                var errorWrapper = function (message) {
                    error(new PositionError(PositionError.POSITION_UNAVAILABLE, message));
                };

                // It doesn't make sense to track multiple callbacks if the point is to conserve battery,
                // so we will ignore the watcher ID and only track a single callback context at any given time for position watching.
                exec(successWrapper, errorWrapper, 'Geolocation', 'beginNativeWatch', [args]);

            } else {
                var geo = cordova.require('cordova/modulemapper').getOriginalSymbol(window, 'navigator.geolocation'); // eslint-disable-line no-undef
                nativeWatchOptions.id = geo.watchPosition(success, error, args);
            }

            pluginToNativeWatchMap[pluginWatchId] = nativeWatchOptions;
        };

        var fail = function () {
            if (error) {
                error(new PositionError(PositionError.PERMISSION_DENIED, 'Illegal Access'));
            }
        };
        exec(win, fail, 'Geolocation', 'getPermission', []);

        return pluginWatchId;
    },

    clearWatch: function (pluginWatchId) {
        var win = function () {
            var nativeWatchOptions = pluginToNativeWatchMap[pluginWatchId];

            if (!nativeWatchOptions) {
                return;
            }

            if (nativeWatchOptions.usesFrequency) {
                exec(null, null, 'Geolocation', 'clearNativeWatch', []);

            } else {
                var geo = cordova.require('cordova/modulemapper').getOriginalSymbol(window, 'navigator.geolocation'); // eslint-disable-line no-undef
                geo.clearWatch(nativeWatchOptions.id);
            }
        };

        exec(win, null, 'Geolocation', 'getPermission', []);
    }
};

module.exports = androidGeolocation;
