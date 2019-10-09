"use strict"

angular.module('oncokbStaticApp')
    .controller('actionableGenesCtrl', function($rootScope, $scope, $location,
                                                $sce,
                                                $q,
                                                $timeout,
                                                DTColumnDefBuilder,
                                                _,
                                                pluralize,
                                                utils,
                                                NgTableParams,
                                                $routeParams,
                                                api) {
        $scope.data = {
            genes: {
                levels: _.reduce($scope.meta.levelButtons, function(current, next) {
                    current[next.level] = [];
                    return current;
                }, {}),
                total: []
            },
            levelColors: $rootScope.data.levelColors,
            tumorTypes: [],
            drugs: [],
            treatments: []
        };
        $scope.filterResults = {
            genes: {
                levels: _.reduce($scope.meta.levelButtons, function(current, next) {
                    current[next.level] = [];
                    return current;
                }, {}),
                total: []
            },
            levels: [],
            levelColors: $rootScope.data.levelColors,
            tumorTypes: [],
            drugs: [],
            treatments: []
        };
        $scope.filters = {
            levels: {}
        };
        $scope.status = {
            pasting: true,
            hasFilter: false,
            loading: false
        };

        $scope.tumorTypes = [];

        $scope.clickGene = function(gene) {
            $location.path('/gene/' + gene);
        };

        $scope.$watch('filters', function(newFilter) {
            var filteredResult = _.cloneDeep($scope.data.treatments);
            var deferred = $q.defer();
            if (newFilter.tumorType) {
                api.findRelevantTumorTypes(newFilter.tumorType.name)
                    .then(function(tumorTypes) {
                        filteredResult = _.filter(filteredResult, function(record) {
                            return _.find(tumorTypes.data, function(tumorType) {
                                return tumorType.name ? tumorType.name === record.tumorType : tumorType.mainType.name === record.tumorType;
                            });
                        });
                    }, function() {
                        filteredResult = _.filter(filteredResult, ['tumorType', newFilter.tumorType.name]);
                    })
                    .finally(function() {
                        deferred.resolve();
                    });
            } else {
                deferred.resolve();
            }

            deferred.promise.finally(function() {
            if (newFilter.gene) {
                filteredResult = _.filter(filteredResult, ['gene', newFilter.gene.name]);
            }
            if (newFilter.drug) {
                filteredResult = _.filter(filteredResult, function(record) {
                    return record.drugs.includes(newFilter.drug.name);
                });
            }
            if (newFilter.levels) {
                var validLevelFilters = getEnabledLevels(newFilter);
                // Don't filter if no level selected.
                if (validLevelFilters.length > 0) {
                    filteredResult = _.filter(filteredResult, function(record) {
                        return validLevelFilters.includes($scope.match2KeyLevel(record.level));
                    });
                }
            }
            $scope.filterResults = getStats(filteredResult);
            if(!newFilter.drug) {
                $scope.data.drugs = $scope.filterResults.drugs;
            }
            if(!newFilter.tumorType) {
                $scope.data.tumorTypes = $scope.filterResults.tumorTypes;
            }
            if(!newFilter.gene) {
                $scope.data.genes.total = $scope.filterResults.genes.total;
            }
            if (newFilter.gene || newFilter.tumorType || newFilter.drug) {
                $scope.data.genes.levels = $scope.filterResults.genes.levels;
            }
            $scope.tableParams = getNgTable(filteredResult);
            $scope.status.hasFilter = _.keys(newFilter).length !== 0 && (getEnabledLevels(newFilter).length > 0 || newFilter.tumorType || newFilter.gene || newFilter.drug);
            if (!newFilter.gene && !newFilter.tumorType && !newFilter.drug) {
                $scope.data.genes.levels = getStats(_.cloneDeep($scope.data.treatments)).genes.levels;
            }
            });
        }, true);

        $scope.buttonShouldBeDisabled = function(level) {
            return $scope.data.genes.levels[level] ? ($scope.data.genes.levels[level].length === 0) : !$scope.filters.levels[level];
        };

        $scope.clickLevelButtonEvent = function(level) {
            $scope.filters.levels[level] = !$scope.filters.levels[level];
        };

        $scope.resetFilters = function() {
            $scope.filters = {
                levels: {}
            };
        };

        $scope.getFilteredResultStatement = function() {
            return $sce.trustAsHtml('<span style="font-weight: bold;">Showing ' + $scope.filterResults.treatments.length + ' biomarker-drug ' + pluralize('association', $scope.filterResults.treatments.length) + '</span> ('+$scope.filterResults.genes.total.length+' ' + pluralize('gene', $scope.filterResults.genes.total.length) + ', '+$scope.filterResults.tumorTypes.length+' '+pluralize('tumor type', $scope.filterResults.tumorTypes.length)+', ' + $scope.filterResults.levels.length+' '+ pluralize('level', $scope.filterResults.levels.length) +' of evidence)');
        };

        $scope.pluralizeString = function(string, number) {
            return pluralize(string, number);
        };

        $scope.getHugoSymbolLinkout = function(gene) {
            return utils.getHugoSymbolLinkout(gene);
        };

        $scope.getAlterationCellContent = function(hugoSymbol, alterations) {
            return alterations.map(function(alteration) {
                return utils.getAlterationCellContent(hugoSymbol, alteration);
            }).join(', ');
        };

        $scope.downloadFilteredAssociations = function() {
            var content = [['Level', 'Gene', 'Alterations', 'Tumor Type', 'Drugs'].join('\t')];
            var fileName = 'oncokb_biomarker_drug_associations.tsv';

            _.each($scope.tableParams.data, function(item) {
                content.push([item.level, item.gene, item.alterations.join(', '), item.tumorType, item.drugs].join('\t'));
            });
            var blob = new Blob([content.join('\n')], {
                type: 'text/plain;charset=utf-8;',
            });
            saveAs(blob, fileName);
        };

        function getEnabledLevels(filters) {
            if (!filters.levels) {
                return [];
            }
            return _.reduce(filters.levels, function(acc, status, level) {
                if (status) {
                    acc.push(level);
                }
                return acc;
            }, []);
        }

        function getAllMainTypes() {
            var deferred = $q.defer();
            api.getAllMainTypes()
                .then(function(response) {
                    $scope.tumorTypes = _.uniqBy($scope.tumorTypes.concat(response.data.filter(function(record) {
                        return !record.trim().endsWith("NOS");
                    }).map(function(mainType) {
                        return {
                            type: 'Main Type',
                            name: mainType
                        };
                    })), 'name');
                    deferred.resolve();
                }, function() {
                    deferred.resolve();
                });
            return deferred.promise;
        }

        function getTreatmentsMetadata() {
            $scope.status.loading = true;
            $q.all([ajaxGetTreatments(), getAllMainTypes()]).finally(function() {
                $scope.status.loading = false;
            });
        }

        function getNgTable(data) {
            return new NgTableParams({
                sorting: {level: 'asc', gene: 'asc'},
                count: 500
            }, {
                counts: [],
                defaultSort: 'asc',
                dataset: data
            });
        }

        function ajaxGetTreatments() {
            var deferred = $q.defer();
            var levels = [{
                url: 'LEVEL_1',
                variable: 'one'
            }, {
                url: 'LEVEL_2A',
                variable: 'two'
            }, {
                url: 'LEVEL_3A',
                variable: 'three'
            }, {
                url: 'LEVEL_4',
                variable: 'four'
            }, {
                url: 'LEVEL_R1',
                variable: 'r1'
            }, {
                url: 'LEVEL_R2',
                variable: 'r2'
            }];

            api.getEvidencesBylevel()
                .then(function(result) {
                    try {
                        let treatments = [];
                        _.forEach(result.data, function(content, levelOfEvidence) {
                            var level = _.find(levels, function(_level) {
                                return _level.url === levelOfEvidence;
                            });
                            if (level !== undefined) {
                                treatments = treatments.concat(getTreatments(content));
                            }
                        });

                        $scope.data = getStats(treatments);
                        if ($routeParams.filterType && $routeParams.filter) {
                            if ($routeParams.filterType === 'level') {
                                if ($scope.filters.levels === undefined) {
                                    $scope.filters.levels = {};
                                }
                                $scope.filters.levels[$scope.match2KeyLevel($routeParams.filter)] = true;
                            } else {
                                if ($scope.filters[$routeParams.filterType] === undefined) {
                                    $scope.filters[$routeParams.filterType] = {};
                                }
                                $scope.filters[$routeParams.filterType] = {
                                    name: $routeParams.filter
                                };
                            }
                        } else {
                            $scope.filterResults = $scope.data;
                            var tempTTs = $scope.tumorTypes.concat($scope.filterResults.tumorTypes.map(function(tumorType) {
                                return {
                                    type: 'curated',
                                    name: tumorType.name
                                };
                            }));
                            $scope.tumorTypes = _.uniqBy(tempTTs, 'name');
                            $scope.tableParams = getNgTable($scope.data.treatments);
                        }
                        deferred.resolve();
                    } catch (error) {
                        deferred.reject(error);
                    }
                });

            return deferred.promise;
        }

        function reduceObject2Array(data) {
            return _.reduce(data, function(acc, data, key) {
                acc.push({
                    name: key,
                    count: data
                });
                return acc;
            }, []);
        }

        $scope.match2KeyLevel = function(level) {
            return _.replace(level, new RegExp('[AB]'), '');
        };

        function getStats(treatments) {
            var genes = {
                levels: {},
                total: {}
            };
            var tumorTypes = {};
            var drugs = {};

            _.forEach(treatments, function(treatment) {
                var level = $scope.match2KeyLevel(treatment.level);
                var gene = treatment.gene;
                var tumorType = treatment.tumorType;
                if (genes.levels[level] === undefined) {
                    genes.levels[level] = {};
                }
                if (genes.levels[level][gene] === undefined) {
                    genes.levels[level][gene] = 0;
                }
                if (genes.total[gene] === undefined) {
                    genes.total[gene] = 0;
                }
                genes.levels[level][gene]++;
                genes.total[gene]++;

                if (tumorTypes[tumorType] === undefined) {
                    tumorTypes[tumorType] = 0;
                }
                tumorTypes[tumorType]++;

                _.forEach(treatment.treatments, function(treatment) {
                    _.forEach(treatment.drugs, function(drug) {
                        if (drugs[drug.drugName] === undefined) {
                            drugs[drug.drugName] = 0;
                        }
                        drugs[drug.drugName]++;
                    });
                });
            });

            var geneLeveles = _.mapValues(genes.levels, function(gene) {
                return reduceObject2Array(gene);
            });
            return {
                genes: {
                    levels: geneLeveles,
                    total: reduceObject2Array(genes.total)
                },
                levels: _.keys(geneLeveles),
                treatments: treatments,
                drugs: reduceObject2Array(drugs),
                tumorTypes: reduceObject2Array(tumorTypes)
            };
        }

        function getTreatments(metadata) {
            var treatments = [];
            if (_.isArray(metadata)) {
                _.forEach(metadata, function(item) {
                    var treatment = {
                        level: item.levelOfEvidence.replace('LEVEL_', ''),
                        gene: item.gene.hugoSymbol || 'NA',
                        alterations: item.alterations.map(function(alt) {
                            return alt.name ? alt.name : alt.alteration;
                        }).sort(),
                        tumorType: utils.getCancerTypeNameFromOncoTreeType(item.oncoTreeType),
                        treatments: item.treatments,
                        drugs: item.treatments.map(function(treatment) {
                            return treatment.drugs.map(function(drug) {
                                return drug.drugName;
                            }).sort().join(' + ');
                        }).sort().join(', ')
                    };
                    treatments.push(treatment);
                });
            }
            return treatments;
        }

        getTreatmentsMetadata();
    });

