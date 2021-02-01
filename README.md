![GitHub release (latest by date)](https://img.shields.io/github/v/release/hmrc/play-fsm) ![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/hmrc/play-fsm)

play-fsm
===

A pattern for stateful microservices using Scala and Play Framework
===

What `stateful` means?
---

Previous user interactions dictate the current behaviour of the service.

The user to complete a process has to provide information in multiple stages in the expected order.

Think multi-page tax form, shopping cart or financial transaction.

Objectives of stateful microservice
---

- Keep track of user steps and provided information
- Offer user the right directions at each stage
- Accept only expected actions and inputs
- Protect the integrity of collected data
- Conclude process only if all requirements met

Common implementation pitfals
---

- Technical solution details overshadow and hinder business process logic,
- Process state derived informally from the collected input,
- Loose set of scattered helper methods decide the process rules,
- New features can surpass existing checks, intentionally or by mistake,
- Process orchestrated and validated mainly on the UI level,
- Inconsistent application of process rules on different layers,
- Unforeseen user interactions lead to process corruption,
- Testing features in isolation requires complex and slow process warming.

FSM approach
---

Looking at the objectives, it becomes apparent we can model our requirements using a finite state machine concept. Process steps are states, and transitions represent user actions. Each state holds only the necessary information, and each transition can bring new user input or call external services. All steps and transitions form a single connected graph.

How `play-fsm` helps?
---

Play-FSM brings a pattern, `an order and method`, for stateful microservice implementation, with the following properties:

- User flow logic is decoupled from framework and protocol details,
- User flow logic is defined explicitly and in a single place,
- Service layer knows the current state of the journey of every user,
- Transition is the only way to change the current state,
- Controller layer maps HTTP endpoints to transitions,
- Controller layer maps states to the UI views for rendering,
- Controller layer maps states to HTTP endpoints for redirections.

What is in the `play-fsm` box?
---

User flow (journey) model is written as a plain static Scala code, consisting of:
- [State]s, represented by case object or case classes,
- [Transition]s, represented by simple partial functions of type `State => Future[State]`.

Library provides specialized traits for implementing Play service and controller components.

Rich Actions DSL offers a wide variety of ready-to-use mappings between endpoints and transitions.

Benefits of using `play-fsm`
---

- User flow logic is visible and easy to reason about,
- Overall service behaviour is consistent and predictable,
- Adding new or modifying existing features with confidence is super-fast,
- Lightweight nature of journey model makes it perfect for extensive and fast unit testing,
- Decoupled state management makes isolated integration tests both more straightforward and faster.

API details
---
The key concept is a **Journey**.
Each journey represents separate business process/transaction.
Each journey has a single root state.

Journey is only loosely related to the HTTP and user session, in fact, depending on the state persistence
implementation it can be a part of a user session, span multiple sessions or cross authorisation boundary. 
It is expected of an application to consist of one or more journeys. 

Journey is build out of **State**s, **Transition**s and **Merger**s. 

**State** can be anything but usually it will be a set of case classes or objects representing the progress and data of a business process or transaction.

**Transition** is a means of moving forward from one state to the another. It's type is a partial async function `State => Future[State]`. 
Transition should be a *pure* function, depending only on its own parameters and the current state. 
External async requests to the upstream services should be provided as a function-type parameters. 

**Merger** is a partial function of type `(S <: State, State) => S`, used to reconcile current and previous states when rolling back the journey. 

**Breadcrumbs** is a reversed list of previous states (the head is the last one) forming journey history.
History is available only to the *service* and *controller* layers, *model* by design has no implicit knowledge of the history.
Breadcrumbs allow for safe back-linking and rolling history back.

If needed, both *controller* and *service* can exercise fine control over the journey history.

Mixins
---
- `JourneyModel` state and transition model
- `JourneyService` basic state and breadcrumbs services
- `PersistentJourneyService` persistence plug-in
- `JourneyController` base controller trait with common action builders
- `JsonStateFormats` state to json serialization and deserialization builder
- `JourneyIdSupport` mix into JourneyController to feature unique journeyId in the Play session

Advanced examples:
---
- Agent Invitations: 
    - Models: <https://github.com/hmrc/agent-invitations-frontend/tree/master/app/uk/gov/hmrc/agentinvitationsfrontend/journeys>
    - Controllers: <https://github.com/hmrc/agent-invitations-frontend/blob/master/app/uk/gov/hmrc/agentinvitationsfrontend/controllers>

- Home Office Settled Status:
    - Model: <https://github.com/hmrc/home-office-settled-status-frontend/blob/master/app/uk/gov/hmrc/homeofficesettledstatus/journeys>
    - Controller: <https://github.com/hmrc/home-office-settled-status-frontend/blob/master/app/uk/gov/hmrc/homeofficesettledstatus/controllers>

- Trader Services:
    - Models: https://github.com/hmrc/trader-services-route-one-frontend/tree/master/app/uk/gov/hmrc/traderservices/journeys
    - Controllers: <https://github.com/hmrc/trader-services-route-one-frontend/tree/master/app/uk/gov/hmrc/traderservices/controllers>

Best practices
---
- Keep a single model definition in a single file.
- Name states as nouns and transitions as verbs.
- Carefully balance when to introduce new state and when to add properties to the existing one(s).
- Use a rule of thumb to keep only relevant data in the state.
- Try to avoid optional properties; their presence usually suggest splitting the state.
- Do NOT put functions and framework components in a state; a state should be immutable and serializable.
- Define transitions using curried methods. It works well with action builders.
- When the transition depends on some external operation(s), pass it as a function(s).

How-tos
===

Where to start?
---

You can start with g8 template available at <https://github.com/hmrc/template-play-27-frontend-fsm.g8>.

How to add play-fsm library to your existing service?
---

In your SBT build add:

    resolvers += Resolver.bintrayRepo("hmrc", "releases")
    
    libraryDependencies += "uk.gov.hmrc" %% "play-fsm" % "0.x.0-play-27"
    
or 
    
    libraryDependencies += "uk.gov.hmrc" %% "play-fsm" % "0.x.0-play-26"
    
How to build a model?
---

- First, try to visualise user interacting with your application in any possible way. 
- Think about translating pages and forms into a diagram of states and transitions.
- Notice the required inputs and knowledge accumulated at each user journey stage.
- Create a new model object and define there the rules of the game, see an example in <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyModel.scala>.
- Create a unit test to validate all possible states and transitions outcomes, see an example in <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyModelSpec.scala>.

How to persist in the state?
---

- play-fsm is not opinionated about state persistence and session management choice but provides an abstract API in the `PersistentJourneyService`.
- `JsonStateFormats` helps to encode/decode state to JSON when using MongoDB or an external REST service, e.g. <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyStateFormats.scala>.

How to define a controller?
---

- Create a controller as usual extending `JourneyController` trait.
- Decide 2 things:
    - How to wire action calls into model transitions? Use provided Actions DSL helpers selection, see <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyController.scala>.
    - How to display the state after GET call? Implement `renderState`.
- Map all GET calls to states, implement `getCallFor` method
- Consider using `backlinkFor` or `backlinkToMostRecent[S]` method to get back link call given breadcrumbs
- GET actions should be idempotent, i.e. should only render existing or historical state.
- POST actions should always invoke some state transition and be followed by a redirect.

What is RequestContext type parameter?
---
The type parameter `[RequestContext]` is the type of an implicit context information expected to be
available throughout every action body and in the bottom layers (i.e. persistence, connectors). 
In the HMRC case it is a `HeaderCarrier`.

Inside your `XYZController extends JourneyController[MyContext]` implement:

    override implicit def context(implicit rh: RequestHeader): MyContext = MyContext(...)

Patterns
===

Model patterns
---

#### State definition patterns

- finite state: sealed trait and a set of case classes/objects

```
    sealed trait State
    
    object State {
        case object Start extends State
        case class Continue(arg: String) extends State
        case class Stop(result: String) extends State
        case object TheEnd
    }
```

- continuous state: class or trait or a primitive value wrapper

```
    type State = String
```

- marking error states

```
    sealed trait State
    sealed trait IsError
    
    object State {
        ...
        case class Failed extends State with IsError
    }
```

#### Transition definition patterns

- simple transition depending only on a current state

```
    val start = Transition {
        case State.Start        => goto(State.Start)
        case State.Continue(_)  => goto(State.Start)
        case State.Stop(_)      => stay
    }
```

- transition depending on a state and single parameter

```
    def stop(user: User) = Transition {
        case Start              => goto(Stop(""))
        case Continue(value)    => goto(Stop(value))
        case Stop()             => stay
    }
```

- transition depending on a state and multiple parameters

```
    def continue(user: User)(input: String) = Transition {
        case Start              => goto(Continue(arg))
        case Continue(value)    => goto(Continue(value + "," + input))
    }
```

- transition depending on a state, parameter and an async data source

```
    def continue(user: User)(externalCall: Int => Future[String]) = Transition {
        case Start              => externalCall.map(input => goto(Continue(input)))
        case Continue(value)    => externalCall.map(input => goto(Continue(value + "," + input)))
    }
```

#### Merger definition pattern

```
    object Mergers {

        def toStart =
            Merger[State.Start.type] {
                case (state, _) => State.Start
            }

        def toContinue =
            Merger[State.Continue] {
                case (state, State.Stop(curr)) => state.copy(arg = curr + "_" + curr)
            }

    }
```

### Controller patterns

- render the current state

```
    val showCurrent: Action[AnyContent] = 
        actions
            .showCurrentState
```
or
```
    val showCurrent: Action[AnyContent] = 
        actions
            .showCurrentState
            .displayUsing(implicit request => renderState2)
```

- render the current state if matches the type S

```
    val showStart: Action[AnyContent] = 
        actions
            .show[State.Start.type]
```

- render the state matching the type S, eventually rolling the history back

```
    val showStart: Action[AnyContent] = 
        actions
            .show[State.Start.type]
            .orRollback
```

- render the state matching the pattern (type), eventually rolling the history back and merging historic state with the current one

```
    val showStart: Action[AnyContent] = 
        actions
            .show[State.Start.type]
            .orRollbackUsing(Mergers.toStart)
```

- render the current state if matches the pattern (type), eventually rolling the history back, otherwise apply the transition and display/redirect to the new state

```
    val showStart: Action[AnyContent] = 
        actions
            .show[State.Start.type]
            .orRollback
            .orApply(Transitions.start)
```

- render the current state if matches the pattern (type), eventually rolling the history back and merging historic state with the current one, otherwise apply the transition and display/redirect to the new state

```
    val showStart: Action[AnyContent] = 
        actions
            .show[State.Start.type]
            .orRollbackUsing(Mergers.toStart)
            .orApply(Transitions.start)
```

- apply the transition to the current state and redirect to the new state

```
    val stop: Action[AnyContent] = 
        actions
            .apply(Transitions.stop)
```

- apply the transition to the current state and redirect to the new state if has changed, otherwise re-display the state

```
    val stop: Action[AnyContent] = 
        actions
            .apply(Transitions.stop)
            .redirectOrDisplayIfSame
```

- apply the transition to the current state and display if new state is of expected type, otherwise redirect to the new state

```
    val stop: Action[AnyContent] =
        actions
            .apply(Transitions.stop)
            .redirectOrDisplayIf[State.Stop]
```

- bind the form and apply transition if success, otherwise redirect to the current page with failed form flashed in

```
    val processForm: Action[AnyContent] = 
        actions
            .bindForm(Forms.myForm)
            .apply(Transitions.processFormInput)
```

- bind the form, derived from the current state, and apply transition if success, otherwise redirect to the current page with failed form flashed in

```
    val processForm: Action[AnyContent] = 
        actions
            .bindFormDerivedFromState(currentState => 
                Forms.myForm(currentState.foo)
            )
            .apply(Transitions.processFormInput)
```

- parse json payload and apply transition if success, otherwise use recovery strategy

```
    val processJson: Action[AnyContent] = 
        actions
            .parseJson[MyPayload]
            .apply(Transitions.processMyPayload)
            .recover { case _ => BadRequest }
```
or
```
    val processJson: Action[AnyContent] = 
        actions
            .parseJsonWithFallback[MyPayload](BadRequest)
            .apply(Transitions.processMyPayload)
```

- run some task after transition

```
    val runTask: Action[AnyContent] = 
        actions
            .parseJson[MyPayload]
            .apply(Transitions.processMyPayload)
            .recover { case _ => BadRequest }
            .thenRunTask(_ => Future(println("Hello World!")))
```

- retrieve something before transition

```
    val runTask: Action[AnyContent] = 
        actions
            .getAsync(_ => Future.successful(1))
            .bindForm(ArgForm)
            .apply(Transitions.continue)
```

- clear or refine the journey history after transition (cleanBreadcrumbs)

```
    val showStart: Action[AnyContent] = 
        actions
            .show[State.Start.type]
            .orRollback
            .andCleanBreadcrumbs()
```

- use an alternative state renderer

```
    val stop: Action[AnyContent] = 
        actions
            .apply(Transitions.stop)
            .displayUsing(implicit request => someRenderState2)
```
or 
```
    val stop: Action[AnyContent] = 
        actions
            .show[State.Stop]
            .orRollback
            .displayUsing(implicit request => someRenderState2)
```

- wait for an async change of state (e.g. waiting for a backend callback)

```
    val stop: Action[AnyContent] = 
        actions
            .waitForStateAndDisplay[State.continue](30) //seconds
            .recover{ case e: TimeoutException => BadRequest }

    val stop: Action[AnyContent] = 
        actions
            .waitForStateThenRedirect[State.continue](30) //seconds
            .orApply(Transitions.forceContinue)       
```

- use custom error recovery strategy

```
    val stop: Action[AnyContent] = 
        actions
            .apply(Transitions.stop)
            .recover {case _: Exception => BadRequest}


    val stop: Action[AnyContent] = 
        actions
            .apply(Transitions.stop)
            .recoverWith(implict request => {case _: Exception => Future.successful(BadRequest)})     
```

- to enforce unique journeyId in the session mixin `JourneyIdSupport`

```
    ... extends Controller
            with JourneyController[MyContext]
            with JourneyIdSupport[MyContext] {
```

Service patterns
---

- do not keep error states in the journey history (breadcrumbs)

```
    override val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs =
        _.filterNot(s => s.isInstanceOf[model.IsError])
```

- keep only previous step

```
    override val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs =
        _.take(1)
```

Authorization patterns
---

- wrap your own authorisation logic returning some `User` entity

```
    val asUser: WithAuthorised[User] = { implicit request => body =>
        myAuthFunction(request) match {
            case None       => Future.failed(...)
            case Some(user) => body(user)
        }
      }
```

- transition only when user has been authorized

```
    val stop: Action[AnyContent] = actions
        .whenAuthorisedWithRetrievals(asUser)
        .apply(Transitions.stop)
```

- render the current state when user has been authorized

```
    val showCurrent: Action[AnyContent] = 
        actions
        .whenAuthorisedWithRetrievals(asUser)
        .showCurrentState
```

- display or redirect only when user has been authorized

```
    val stop: Action[AnyContent] = 
        actions
            .whenAuthorisedWithRetrievals(asUser)
            .apply(Transitions.stop)
            .redirectOrDisplayIfSame
```
or
```
    val stop: Action[AnyContent] = 
        actions
            .whenAuthorised(asUser)
            .apply(Transitions.stop(0))
            .redirectOrDisplayIfSame
```

- display page for an authorized user only

```
    val showContinue: Action[AnyContent] = 
        actions
            .whenAuthorised(asUser)
            .show[State.Continue]
            .orRollback
```

-  for an authorized user only, render the current state if matches the pattern (type), otherwise apply the transition and redirect to the resulting state

```
    val showContinue: Action[AnyContent] = 
        actions
            .whenAuthorisedWithRetrievals(asUser)
            .show[State.Continue]
            .orRollback
            .orApplyWithRequest(implicit request => Transitions.continue)
```
or
```
    val showContinue: Action[AnyContent] = 
        actions
            .whenAuthorised(asUser)
            .show[State.Continue]
            .orRollback
            .orApplyWithRequest(implicit request => Transitions.continue(0))
```

- for an authorized user only, render the current state if matches the pattern (type), otherwise rollback using merger

```
    val showContinue: Action[AnyContent] = 
        actions
            .whenAuthorised(asUser)
            .show[State.Continue]
            .orRollbackUsing(Mergers.toContinue)
```

- for an authorized user only, retrieve additional information, then apply the transition to the current state and redirect to the resulting state

```
    val showContinue: Action[AnyContent] = 
        actions
            .whenAuthorised(asUser)
            .getAsync(_ => Future.successful(1))
            .apply(i => Transitions.stop(i))
```
or
```
    val showContinue: Action[AnyContent] = 
        actions
            .whenAuthorisedWithRetrievals(asUser)
            .getAsync(_ => Future.successful(1))
            .apply(a => b => Transitions.stop(a+b))
```

