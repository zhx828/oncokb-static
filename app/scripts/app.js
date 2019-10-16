'use strict';

/**
 * @ngdoc overview
 * @name oncokbStaticApp
 * @description
 * # oncokbStaticApp
 *
 * Main module of the application.
 */
angular
    .module('oncokbStaticApp', [
        'ngAnimate',
        'ngCookies',
        'ngResource',
        'ngRoute',
        'ngSanitize',
        'ngTouch',
        'ui.router',
        'datatables',
        'datatables.bootstrap',
        'ui.bootstrap',
        'ngTable',
        'ui.select',
        'swaggerUi'
    ])
    .constant('_', window._)
    .constant('marked', window.marked)
    .constant('MutationDetailsEvents', window.MutationDetailsEvents)
    .constant('PileupUtil', window.PileupUtil)
    .constant('MutationMapper', window.MutationMapper)
    .constant('MutationCollection', window.MutationCollection)
    .constant('Plotly', window.Plotly)
    .constant('Sentry', window.Sentry)
    .constant('pluralize', window.pluralize)
    .constant('legacyLink', 'legacy-api/')
    .constant('privateApiLink', 'api/private/')
    .constant('apiLink', 'api/v1/')
    .constant('onLocalhost', location.hostname === 'localhost' || location.hostname === '127.0.0.1')
    .config(function($routeProvider, $provide, $httpProvider, Sentry, onLocalhost, $locationProvider) {
        $routeProvider
            .when('/', {
                templateUrl: 'views/main.html',
                controller: 'HomeCtrl'
            })
            .when('/levels', {
                title: 'Levels of Evidence',
                templateUrl: 'views/levels.html',
                controller: 'MainCtrl'
            })
            .when('/team', {
                title: 'OncoKB Team',
                templateUrl: 'views/team.html',
                controller: 'MainCtrl'
            })
            .when('/gene/:geneName', {
                templateUrl: 'views/gene.html',
                controller: 'GeneCtrl',
                controllerAs: 'gene'
            })
            .when('/genes', {
                title: 'All curated genes',
                templateUrl: 'views/genes.html',
                controller: 'GenesCtrl',
                controllerAs: 'genes'
            })
            .when('/gene/:geneName/:alterationName', {
                templateUrl: 'views/gene.html',
                controller: 'GeneCtrl',
                controllerAs: 'gene'
            })
            .when('/gene/:geneName/alteration/:alterationName', {
                redirectTo: '/gene/:geneName/:alterationName'
            })
            .when('/gene/:geneName/variant/:alterationName', {
                redirectTo: '/gene/:geneName/:alterationName'
            })
            .when('/about', {
                title: 'About OncoKB',
                templateUrl: 'views/about.html',
                controller: 'AboutCtrl',
                controllerAs: 'about'
            })
            .when('/actionableGenes', {
                title: 'Actionable Genes, Alterations, Tumor Types, and Drugs',
                templateUrl: 'views/actionalGenes.html',
                controller: 'actionableGenesCtrl',
                controllerAs: 'actionableGenes'
            })
            .when('/actionableGenes/:filterType/:filter', {
                title: 'Actionable Genes, Alterations, Tumor Types, and Drugs',
                templateUrl: 'views/actionalGenes.html',
                controller: 'actionableGenesCtrl',
                controllerAs: 'actionableGenes'
            })
            .when('/terms', {
                title: 'Usage Terms',
                templateUrl: 'views/license.html',
                controller: 'LicenseCtrl',
                controllerAs: 'license'
            })
            .when('/updates', {
                redirectTo: '/news'
            })
            .when('/news', {
                title: 'Latest News',
                templateUrl: 'views/news.html',
                controller: 'NewsCtrl',
                controllerAs: 'news'
            })
            .when('/dataAccess', {
                title: 'Access OncoKB Data',
                templateUrl: 'views/dataaccess.html',
                controller: 'DataaccessCtrl',
                controllerAs: 'dataAccess'
            })
            .when('/cancerGenes', {
                title: 'OncoKB Cancer Gene List',
                templateUrl: 'views/cancerGenes.html',
                controller: 'CancerGenesCtrl',
                controllerAs: 'cancerGenes'
            })
            .otherwise({
                redirectTo: '/'
            });

        if(!onLocalhost) {
            $provide.decorator('$exceptionHandler', function() {
                return function(exception) {
                    console.log(exception);
                    Sentry.captureException(exception);
                };
            });

            $httpProvider.interceptors.push('errorHttpInterceptor');
        }

        $locationProvider.html5Mode(true);
    });

angular.module('oncokbStaticApp').run(
    function($timeout, $rootScope, $location, _, api, swaggerModules, markedSwagger, $window) {
        $rootScope.meta = {
            clinicalTableSearchKeyWord: '',
            levelsDesc: {
                '1': 'FDA-recognized biomarker predictive of response to an FDA-approved drug in this indication',
                '2A': 'Standard of care biomarker predictive of response to an FDA-approved drug in this indication',
                '2B': 'Standard of care biomarker predictive of response to an FDA-approved drug in another indication but not standard of care for this indication',
                '3A': 'Compelling clinical evidence supports the biomarker as being predictive of response to a drug in this indication but neither biomarker and drug are standard of care',
                '3B': 'Compelling clinical evidence supports the biomarker as being predictive of response to a drug in another indication but neither biomarker and drug are standard of care',
                '4': 'Compelling biological evidence supports the biomarker as being predictive of response to a drug but neither biomarker and drug are standard of care',
                'R1': 'Standard of care biomarker predictive of resistance to an FDA-approved drug in this indication',
                'R2': 'Compelling clinical evidence supports the biomarker as being predictive of resistance to a drug',
            },
            levelsDescHtml: {
                '1': '<span><b>FDA-recognized</b> biomarker predictive of response to an <b>FDA-approved</b> drug <b>in this indication</b></span>',
                '2A': '<span><b>Standard of care</b> biomarker predictive of response to an <b>FDA-approved</b> drug <b>in this indication</b></span>',
                '2B': '<span><b>Standard of care</b> biomarker predictive of response to an <b>FDA-approved</b> drug <b>in another indication</b> but not standard of care for this indication</span>',
                '3A': '<span><b>Compelling clinical evidence</b> supports the biomarker as being predictive of response to a drug <b>in this indication</b> but neither biomarker and drug are standard of care</span>',
                '3B': '<span><b>Compelling clinical evidence</b> supports the biomarker as being predictive of response to a drug <b>in another indication</b> but neither biomarker and drug are standard of care</span>',
                '4': '<span><b>Compelling biological evidence</b> supports the biomarker as being predictive of response to a drug but neither biomarker and drug are standard of care</span>',
                'R1': '<span><b>Standard of care</b> biomarker predictive of <b>resistance</b> to an <b>FDA-approved</b> drug <b>in this indication</b></span>',
                'R2': '<span><b>Compelling clinical evidence</b> supports the biomarker as being predictive of <b>resistance</b> to a drug</span>',
            },
            numbers: {
                main: {
                    gene: 0,
                    alteration: 0,
                    tumorType: 0,
                    drug: 0,
                },
                levels: {}
            },
            levelButtons: [{
                level: '1',
                className: 'level-1',
                desc: 'FDA-approved'
            }, {
                level: '2',
                className: 'level-2',
                desc: 'Standard care'
            }, {
                level: '3',
                className: 'level-3',
                desc: 'Clinical evidence'
            }, {
                level: '4',
                className: 'level-4',
                desc: 'Biological evidence'
            }, {
                level: 'R1',
                className: 'level-R1',
                desc: 'Standard care'
            }, {
                level: 'R2',
                className: 'level-R2',
                desc: 'Clinical evidence'
            }]
        };

        $rootScope.view = {
            currentPage: '/'
        };
        $rootScope.meta.view = {
            subNavItems: [{
                content: '595 Genes',
                link: '/genes'
            }, {content: '4472 Variants'}, {content: '38 Tumor Types'}]
        };
        $rootScope.data = {
            lastUpdate: 'August 28, 2019',
            version: '1.23',
            levelColors: {
                '1': '#33A02C',
                '2': '#1F78B4',
                '2A': '#1F78B4',
                '2B': '#80B1D3',
                '3': '#984EA3',
                '3A': '#984EA3',
                '3B': '#BE98CE',
                '4': '#424242',
                'R1': '#EE3424',
                'R2': '#F79A92',
                'R3': '#FCD6D3',
                'Other': 'grey'
            },
            citationURL: 'When using OncoKB, please cite: ' +
            '<a href="https://ascopubs.org/doi/full/10.1200/PO.17.00011" ' +
            'target="_blank">Chakravarty et al., JCO PO 2017</a>.'
        };

        api.getNumbers('main')
            .success(function(result) {
                $rootScope.meta.numbers.main = {
                    gene: result.gene,
                    alteration: result.alteration,
                    tumorType: result.tumorType,
                    drug: result.drug,
                };
            });

        api.getNumbers('levels')
            .success(function(result) {
                if (_.isArray(result)) {
                    var levels = {};
                    _.each(result, function(item) {
                        if (item.level) {
                            var match = item.level.match(/LEVEL_(R?\d)+/);
                            if (_.isArray(match) && match.length > 1) {
                                if (!levels.hasOwnProperty(match[1])) {
                                    levels[match[1].toString()] = [];
                                }
                                levels[match[1].toString()] = _.union(levels[match[1]], item.genes);
                            }
                        }
                    });

                    $rootScope.meta.numbers.levels = levels;
                }
            });

        $rootScope.$on('$routeChangeStart', function() {
            $rootScope.view.subNavItems = [];
        });
        $rootScope.$on('$routeChangeSuccess', function(event, current) {
            var path = $location.path().split('/') || [];
            if (path.length === 5 && path[3] === 'alteration') {
                $rootScope.view.currentPage = 'alteration';
            } else {
                $rootScope.view.currentPage = path.length > 2 ? path[1] : '';
            }
            if (!$rootScope.view.subNavItems || $rootScope.view.subNavItems.length === 0) {
                $.extend(true, $rootScope.view.subNavItems, $rootScope.meta.view.subNavItems);
            }

            if(current && current.$$route) {
                $window.document.title = current.$$route.title ? current.$$route.title : 'OncoKB';
            }
        });

        swaggerModules.add(swaggerModules.PARSE, markedSwagger);
    })
;

NProgress.start();
angular.element(document).ready(function() {
    angular.bootstrap(document, ['oncokbStaticApp']);

    // Attach scrollTop event from jQuery
    var navObject = jQuery.find('.navbar.navbar-fixed-top');
    jQuery(window).scroll(function() {
        if ($(this).scrollTop() === 0) {
            $(navObject).removeClass('scrolled-box');
        } else if (!$(navObject).hasClass('scrolled-box')) {
            $(navObject).addClass('scrolled-box');
        }
    });
});
angular.element(document).on('show.bs.tab', '.nav-tabs-responsive [data-toggle="tab"]', function(e) {
    var $target = $(e.target);
    var $tabs = $target.closest('.nav-tabs-responsive');
    var $current = $target.closest('li');
    var $parent = $current.closest('li.dropdown');
    $current = $parent.length > 0 ? $parent : $current;
    var $next = $current.next();
    var $prev = $current.prev();

    $tabs.find('>li').removeClass('next prev');
    $prev.addClass('prev');
    $next.addClass('next');
});

jQuery.extend(jQuery.fn.dataTableExt.oSort, {
    'level-asc': function(a, b) {
        var levels = ['4', '3B', '3A', '2B', '2A', 'R1', '1'];
        var _a = levels.indexOf(a);
        var _b = levels.indexOf(b);
        if (_a === -1) {
            return 1;
        }
        if (_b === -1) {
            return -1;
        }
        return _a - _b;
    },
    'level-desc': function(a, b) {
        var levels = ['4', '3B', '3A', '2B', '2A', 'R1', '1'];
        var _a = levels.indexOf(a);
        var _b = levels.indexOf(b);
        if (_a === -1) {
            return 1;
        }
        if (_b === -1) {
            return -1;
        }
        return _b - _a;
    },
    'num-html-pre': function(a) {
        a = '<span>' + a + '</span>';
        var x = String($(a).text()).replace(/(?!^-)[^0-9.]/g, '');
        return x ? parseFloat(x) : Infinity;
    },
    'num-html-asc': function(a, b) {
        return a < b;
    },
    'num-html-desc': function(a, b) {
        return a > b;
    },
    'level-html-pre': function(a) {
        var s = $(a).text();
        var levels = ['4', '3B', '3A', '2B', '2A', 'R1', '1'];
        return levels.indexOf(s);
    },
    'level-html-asc': function(a, b) {
        if (a === -1) {
            return 1;
        } else if (b === -1) {
            return -1;
        }
        return a - b;
    },
    'level-html-desc': function(a, b) {
        if (a === -1) {
            return 1;
        } else if (b === -1) {
            return -1;
        }
        return b - a;
    },
    'oncogenic-html-asc': function(a, b) {
        var levels = ['Inconclusive', 'Likely Neutral', 'Likely', 'Yes'];
        var _a = levels.indexOf(a);
        var _b = levels.indexOf(b);
        if (_a === -1) {
            return 1;
        }
        if (_b === -1) {
            return -1;
        }
        return _a - _b;
    },
    'oncogenic-html-desc': function(a, b) {
        var levels = ['Inconclusive', 'Likely Neutral', 'Likely', 'Yes'];
        var _a = levels.indexOf(a);
        var _b = levels.indexOf(b);
        if (_a === -1) {
            return 1;
        }
        if (_b === -1) {
            return -1;
        }
        return _b - _a;
    }
});

if (!String.prototype.endsWith) {
    String.prototype.endsWith = function(search, this_len) {
        if (this_len === undefined || this_len > this.length) {
            this_len = this.length;
        }
        return this.substring(this_len - search.length, this_len) === search;
    };
}
