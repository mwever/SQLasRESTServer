console.log("juhu1");

var app = angular.module('srsApp', [ 'ngRoute' ]);

console.log("juhu2");
app.controller("SQLRestServerController", function($http, flash) {
	
	var self = this;
	this.experiments = [];
	this.experimentName = "";
	
	this.loadExperimentList = function() {
		$http.get("/v1/admin/experiment/list").then(function(response) {
			self.experiments = [];
			for(index in response.data) {
				self.experiments.push(response.data[index]);
			}
		});
	};
	
	this.getExperimentList = function() {
		return this.experiments;
	};
	
	this.createExperimentToken = function() {
		if(this.experimentName === "") {
			flash.pop({title: "Error", body: "The experiment name must not be empty!", type:"error"});
			return;
		}
		
		$http.get("/v1/admin/experiment?name=" + self.experimentName).then(function successCallback(response) {
			flash.pop({title: "Experiment Created", body: "An experiment token was successfully created for you: " + response.data.token, type:"success"});
			self.loadExperimentList();
		}, function errorCallback(response) {
			flash.pop({title: "Experiment Creation Failed", body: "Could not generate an experiment token due to technical issues.", type:"error"});
		});
	}
	
	this.loadExperimentList();
	
});

app.factory("flash", function() {
	return {
		pop: function(message) {
			switch(message.type) {
				case 'success':
					toastr.success(message.body, message.title);
					break;
				case 'info':
					toastr.info(message.body, message.title);
					break;
				case 'warning':
					toastr.warning(message.body, message.title);
					break;
				case 'error':
					toastr.error(message.body, message.title);
					break;
			}
		}
	};
});