'use strict';

/**
 * @ngdoc directive
 * @name oncokbStaticApp.directive:mainLevel
 * @description
 * # mainLevel
 */
angular.module('oncokbStaticApp')
    .directive('mainLevel', function(pluralize) {
        return {
            templateUrl: 'views/mainLevel.html',
            restrict: 'E',
            scope: {
                level: '=',
                geneCount: '=',
                desc: '=',
                class: '=',
                disableHref: '=?'
            },
            controller: function($scope) {
                if($scope.disableHref === undefined) {
                    $scope.disableHref = false;
                }
            },
            link: function postLink(scope) {
                scope.status = {
                    open: false
                };
                scope.getContent = function() {
                    return scope.geneCount + ' ' + pluralize('Gene', scope.geneCount);
                };
            }
        };
    });
