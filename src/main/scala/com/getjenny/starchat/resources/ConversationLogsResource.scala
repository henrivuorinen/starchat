package com.getjenny.starchat.resources

/**
  * Created by Angelo Leto <angelo@getjenny.com> on 27/06/16.
  */

import akka.NotUsed
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.CircuitBreaker
import akka.stream.scaladsl.Source
import com.getjenny.starchat.entities._
import com.getjenny.starchat.routing._
import com.getjenny.starchat.services.{ConversationLogsService, QuestionAnswerService}
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait ConversationLogsResource extends StarChatResource {

  private[this] val questionAnswerService: QuestionAnswerService = ConversationLogsService
  private[this] val routeName: String = "conversation_logs"

  def clTermsCountRoutes: Route = handleExceptions(routesExceptionHandler) {
    pathPrefix(indexRegex ~ Slash ~ """term_count""" ~ Slash ~
      routeName) { indexName =>
      pathEnd {
        get {
          authenticateBasicAsync(realm = authRealm, authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, indexName, Permissions.read)) {
              extractRequest { request =>
                parameters("field".as[TermCountFields.Value] ?
                  TermCountFields.question, "term".as[String], "stale".as[Long] ? 0) { (field, term, stale) =>
                  val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                  onCompleteWithBreaker(breaker)(questionAnswerService.termCountFuture(indexName, field, term, stale)) {
                    case Success(t) =>
                      completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                        t
                      })
                    case Failure(e) =>
                      log.error("index(" + indexName + ") uri=(" + request.uri +
                        ") method=(" + request.method.name + ") : " + e.getMessage)
                      completeResponse(StatusCodes.BadRequest,
                        Option {
                          ReturnMessageData(code = 101, message = e.getMessage)
                        })
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def clDictSizeRoutes: Route = handleExceptions(routesExceptionHandler) {
    pathPrefix(indexRegex ~ Slash ~ """dict_size""" ~ Slash ~
      routeName) { indexName =>
      pathEnd {
        get {
          authenticateBasicAsync(realm = authRealm, authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, indexName, Permissions.read)) {
              extractRequest { request =>
                parameters("stale".as[Long] ? 0) { stale =>
                  val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                  onCompleteWithBreaker(breaker)(questionAnswerService.dictSizeFuture(indexName, stale)) {
                    case Success(t) =>
                      completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                        t
                      })
                    case Failure(e) =>
                      log.error("index(" + indexName + ") uri=(" + request.uri + ") method=(" +
                        request.method.name + ") : " + e.getMessage)
                      completeResponse(StatusCodes.BadRequest,
                        Option {
                          ReturnMessageData(code = 102, message = e.getMessage)
                        })
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def clTotalTermsRoutes: Route = handleExceptions(routesExceptionHandler) {
    pathPrefix(indexRegex ~ Slash ~ """total_terms""" ~ Slash ~
      routeName) { indexName =>
      pathEnd {
        get {
          authenticateBasicAsync(realm = authRealm, authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, indexName, Permissions.read)) {
              extractRequest { request =>
                parameters("stale".as[Long] ? 0) { stale =>
                  val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                  onCompleteWithBreaker(breaker)(questionAnswerService.totalTermsFuture(indexName, stale)) {
                    case Success(t) =>
                      completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                        t
                      })
                    case Failure(e) =>
                      log.error("index(" + indexName + ") uri=(" + request.uri +
                        ") method=(" + request.method.name + ") : " + e.getMessage)
                      completeResponse(StatusCodes.BadRequest,
                        Option {
                          ReturnMessageData(code = 103, message = e.getMessage)
                        })
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def clQuestionAnswerStreamRoutes: Route = handleExceptions(routesExceptionHandler) {
    pathPrefix(indexRegex ~ Slash ~ """stream""" ~ Slash ~
        routeName) { indexName =>
      pathEnd {
        get {
          authenticateBasicAsync(realm = authRealm,
            authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, indexName, Permissions.stream)) {
              extractRequest { request =>
                val entryIterator = questionAnswerService.allDocuments(indexName)
                val entries: Source[KBDocument, NotUsed] =
                  Source.fromIterator(() => entryIterator)
                complete(entries)
              }
            }
          }
        }
      }
    }
  }

  def clQuestionAnswerRoutes: Route = handleExceptions(routesExceptionHandler) {
    pathPrefix(indexRegex ~ Slash ~ routeName) { indexName =>
      pathEnd {
        post {
          authenticateBasicAsync(realm = authRealm,
            authenticator = authenticator.authenticator) { (user) =>
            extractRequest { request =>
              authorizeAsync(_ =>
                authenticator.hasPermissions(user, indexName, Permissions.write)) {
                parameters("refresh".as[Int] ? 0) { refresh =>
                  entity(as[KBDocument]) { document =>
                    val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                    onCompleteWithBreaker(breaker)(questionAnswerService.create(indexName, document, refresh)) {
                      case Success(t) =>
                        t match {
                          case Some(v) =>
                            completeResponse(StatusCodes.Created, StatusCodes.BadRequest, Option {
                              v
                            })
                          case None =>
                            log.error("index(" + indexName + ") uri=(" + request.uri +
                              ") method=(" + request.method.name + ")")
                            completeResponse(StatusCodes.BadRequest,
                              Option {
                                ReturnMessageData(code = 104, message = "Error indexing new document, empty response")
                              })
                        }
                      case Failure(e) =>
                        log.error("index(" + indexName + ") uri=(" + request.uri +
                          ") method=(" + request.method.name + ") : " + e.getMessage)
                        completeResponse(StatusCodes.BadRequest,
                          Option {
                            ReturnMessageData(code = 105, message = "Error indexing new document")
                          })
                    }
                  }
                }
              }
            }
          }
        } ~
          get {
            authenticateBasicAsync(realm = authRealm,
              authenticator = authenticator.authenticator) { (user) =>
              extractRequest { request =>
                authorizeAsync(_ =>
                  authenticator.hasPermissions(user, indexName, Permissions.read)) {
                  parameters("ids".as[String].*) { ids =>
                    val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                    onCompleteWithBreaker(breaker)(questionAnswerService.readFuture(indexName, ids.toList)) {
                      case Success(t) =>
                        completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                          t
                        })
                      case Failure(e) =>
                        log.error("index(" + indexName + ") uri=(" + request.uri +
                          ") method=(" + request.method.name + ") : " + e.getMessage)
                        completeResponse(StatusCodes.BadRequest,
                          Option {
                            ReturnMessageData(code = 106, message = e.getMessage)
                          })
                    }
                  }
                }
              }
            }
          } ~
          delete {
            authenticateBasicAsync(realm = authRealm,
              authenticator = authenticator.authenticator) { (user) =>
              extractRequest { request =>
                authorizeAsync(_ =>
                  authenticator.hasPermissions(user, indexName, Permissions.write)) {
                  val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                  onCompleteWithBreaker(breaker)(questionAnswerService.deleteAll(indexName)) {
                    case Success(t) =>
                      completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                        t
                      })
                    case Failure(e) =>
                      log.error("index(" + indexName + ") uri=(" + request.uri +
                        ") method=(" + request.method.name + ") : " + e.getMessage)
                      completeResponse(StatusCodes.BadRequest,
                        Option {
                          ReturnMessageData(code = 107, message = e.getMessage)
                        })
                  }
                }
              }
            }
          }
      } ~
        path(Segment) { id =>
          put {
            authenticateBasicAsync(realm = authRealm,
              authenticator = authenticator.authenticator) { (user) =>
              extractRequest { request =>
                authorizeAsync(_ =>
                  authenticator.hasPermissions(user, indexName, Permissions.write)) {
                  parameters("refresh".as[Int] ? 0) { refresh =>
                    entity(as[KBDocumentUpdate]) { update =>
                      val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(questionAnswerService.updateFuture(indexName, id, update, refresh)) {
                        case Success(t) =>
                          completeResponse(StatusCodes.Created, StatusCodes.BadRequest, t)
                        case Failure(e) =>
                          log.error("index(" + indexName + ") uri=(" + request.uri +
                            ") method=(" + request.method.name + ") : " + e.getMessage)
                          completeResponse(StatusCodes.BadRequest,
                            Option {
                              ReturnMessageData(code = 108, message = e.getMessage)
                            })
                      }
                    }
                  }
                }
              }
            }
          } ~
            delete {
              authenticateBasicAsync(realm = authRealm,
                authenticator = authenticator.authenticator) { user =>
                extractRequest { request =>
                  authorizeAsync(_ =>
                    authenticator.hasPermissions(user, indexName, Permissions.write)) {
                    parameters("refresh".as[Int] ? 0) { refresh =>
                      val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(questionAnswerService.delete(indexName, id, refresh)) {
                        case Success(t) =>
                          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, t)
                        case Failure(e) =>
                          log.error("index(" + indexName + ") uri=(" + request.uri +
                            ") method=(" + request.method.name + ") : " + e.getMessage)
                          completeResponse(StatusCodes.BadRequest,
                            Option {
                              ReturnMessageData(code = 109, message = e.getMessage)
                            })
                      }
                    }
                  }
                }
              }
            }
        }
    }
  }

  def clQuestionAnswerSearchRoutes: Route = handleExceptions(routesExceptionHandler) {
    val localRouteName: String = routeName + "_search"
    pathPrefix("""^(index_(?:[a-z]{1,256})_(?:[A-Za-z0-9_]{1,256}))$""".r ~
      Slash ~  localRouteName) { indexName =>
      pathEnd {
        post {
          authenticateBasicAsync(realm = authRealm,
            authenticator = authenticator.authenticator) { user =>
            extractRequest { request =>
              authorizeAsync(_ =>
                authenticator.hasPermissions(user, indexName, Permissions.write)) {
                entity(as[KBDocumentSearch]) { docsearch =>
                  val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                  onCompleteWithBreaker(breaker)(questionAnswerService.search(indexName, docsearch)) {
                    case Success(t) =>
                      completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                        t
                      })
                    case Failure(e) =>
                      log.error("index(" + indexName + ") uri=(" + request.uri +
                        ") method=(" + request.method.name + ") : " + e.getMessage)
                      completeResponse(StatusCodes.BadRequest,
                        Option {
                          ReturnMessageData(code = 110, message = e.getMessage)
                        })
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def clUpdateTermsRoutes: Route = handleExceptions(routesExceptionHandler) {
    pathPrefix(indexRegex ~ Slash ~ """updateTerms""" ~ Slash ~
      routeName) { indexName =>
      pathEnd {
        put {
          authenticateBasicAsync(realm = authRealm, authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, indexName, Permissions.read)) {
              extractRequest { request =>
                entity(as[UpdateQATermsRequest]) { extractionRequest =>
                  val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                  onCompleteWithBreaker(breaker)(questionAnswerService.updateTextTermsFuture(indexName = indexName,
                    extractionRequest = extractionRequest)) {
                    case Success(t) =>
                      completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                        t
                      })
                    case Failure(e) =>
                      log.error("index(" + indexName + ") uri=(" + request.uri +
                        ") method=(" + request.method.name + ") : " + e.getMessage)
                      completeResponse(StatusCodes.BadRequest,
                        Option {
                          ReturnMessageData(code = 111, message = e.getMessage)
                        })
                  }
                }
              }
            }
          }
        } ~ get {
          authenticateBasicAsync(realm = authRealm,
            authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, indexName, Permissions.stream)) {
              entity(as[UpdateQATermsRequest]) { extractionRequest =>
                extractRequest { request =>
                  val entryIterator = questionAnswerService.updateAllTextTerms(indexName = indexName,
                    extractionRequest = extractionRequest)
                  val entries: Source[UpdateDocumentResult, NotUsed] =
                    Source.fromIterator(() => entryIterator)
                  complete(entries)
                }
              }
            }
          }
        }
      }
    }
  }

  def clCountersCacheSizeRoutes: Route = handleExceptions(routesExceptionHandler) {
    pathPrefix(indexRegex ~ Slash ~ """cache""" ~ Slash ~
      routeName) { indexName =>
      pathEnd {
        delete {
          authenticateBasicAsync(realm = authRealm, authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, indexName, Permissions.write)) {
              extractRequest { request =>
                val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                onCompleteWithBreaker(breaker)(
                  Future {
                    questionAnswerService.resetCountersCache
                  }) {
                  case Success(t) =>
                    completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                      t
                    })
                  case Failure(e) =>
                    log.error("index(" + indexName + ") uri=(" + request.uri +
                      ") method=(" + request.method.name + ") : " + e.getMessage)
                    completeResponse(StatusCodes.BadRequest,
                      Option {
                        ReturnMessageData(code = 112, message = e.getMessage)
                      })
                }
              }
            }
          }
        } ~ post {
          authenticateBasicAsync(realm = authRealm, authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, indexName, Permissions.write)) {
              extractRequest { request =>
                entity(as[CountersCacheParameters]) { cacheSize =>
                  val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                  onCompleteWithBreaker(breaker)(
                    Future {
                      questionAnswerService.setCountersCacheParameters(cacheSize)
                    }) {
                    case Success(t) =>
                      completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                        t
                      })
                    case Failure(e) =>
                      log.error("index(" + indexName + ") uri=(" + request.uri +
                        ") method=(" + request.method.name + ") : " + e.getMessage)
                      completeResponse(StatusCodes.BadRequest,
                        Option {
                          ReturnMessageData(code = 112, message = e.getMessage)
                        })
                  }
                }
              }
            }
          }
        } ~ get {
          authenticateBasicAsync(realm = authRealm, authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, indexName, Permissions.read)) {
              extractRequest { request =>
                val breaker: CircuitBreaker = StarChatCircuitBreaker.getCircuitBreaker()
                onCompleteWithBreaker(breaker)(
                  Future {
                    questionAnswerService.countersCacheParameters
                  }) {
                  case Success(t) =>
                    completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                      t
                    })
                  case Failure(e) =>
                    log.error("index(" + indexName + ") uri=(" + request.uri +
                      ") method=(" + request.method.name + ") : " + e.getMessage)
                    completeResponse(StatusCodes.BadRequest,
                      Option {
                        ReturnMessageData(code = 113, message = e.getMessage)
                      })
                }
              }
            }
          }
        }
      }
    }
  }
}
