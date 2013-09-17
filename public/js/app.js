"use strict";

(function(postbinApp, angular) {
  var postbinDash = angular.module("PostbinDash", [], function($interpolateProvider) {
    $interpolateProvider.startSymbol("[[");
    $interpolateProvider.endSymbol("]]");
  });
  postbinApp.postbinDash = postbinDash;

  var Socket = function($rootScope, $window) {
    this.socket = new WebSocket("ws://" + $window.location.host + "/ws");
    this.$rootScope = $rootScope;
  }
  Socket.prototype = {
    on: function(eventName, callback) {
      var that = this;
      this.socket.addEventListener(eventName, function(evt) {
        that.$rootScope.$apply(function() {
          callback.apply(that, [evt.data]);
        });
      });
    },
    send: function(data) {
      this.socket.send(data);
    },
  }
  postbinDash.service("$socket", Socket);

  postbinDash.controller("messageDisplayCtrl", function($scope, $socket, $http) {
    $scope.messages = [];
    $scope.postData = "";
    $scope.socketSendEnabled = true;

    $scope.dataMakeDate = function(data) {
      var datedData = []
      for (var i=0; i < data.length; i++) {
        data[i].time = new Date(data[i].time * 1000);
        datedData.push(data[i]);
      }
      return datedData;
    }

    $scope.replaceData = function(data) {
      $scope.messages = $scope.dataMakeDate(data);
    }
    $scope.replaceData(window.postbinApp.initData.reverse());

    $scope.sendMessage = function() {
      var postData = $scope.postData; 
      if (postData == "") {
        return false;
      }
      if ($scope.socketSendEnabled) {
        // send via websockets
        $socket.send(JSON.stringify({type: "message", data: postData}));
      }
      else {
        // send via XHR
        $http.post(window.location.href + "post", $scope.postData)
          .success(function(){
            $scope.postData = "";
        });
      }
      $scope.postData = "";
    }

    $scope.deleteAllMessages = function() {
      $socket.send(JSON.stringify({type: "delete"}));
    }

    $socket.on("message", function(data) {
      var command = JSON.parse(data);

      if (command.type == "delta") {
        var datedDelta = $scope.dataMakeDate(command.data);
        $scope.messages.unshift.apply($scope.messages, datedDelta);
      }
      else if (command.type == "purge") {
        $scope.messages = [];
      }
    });
  });

})(window.postbinApp = window.postbinApp || {}, angular);
