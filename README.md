# play-fsm
This library provides State Machine building blocks for a stateful Play application.

## Features
- `JourneyModel` state and transition model
- `JourneyService` basic state and breadcrumbs services
- `PersistentJourneyService` persistence plug-in
- `JourneyController` base controller trait with common action builders
- `JsonStateFormats` state to json serialization and deserialization builder
    
## Motivation
Managing adequately complex stateful behaviour in an application is a challenge. 
It is even more of a challenge in a traditional server-oriented web application built on top of a stateless-by-design HTTP protocol.

Common requirements are:
- the application has to accumulate business transaction input based on multiple prior interactions with the user
- depending on the user selections and decisions various business outcomes are possible (including lack of action)
- only a few possible journey paths are permitted, the application has to validate each time if the request can be accepted given the current state
- the application must be able to acquire, cache and show additional data sourced asynchronously from upstream services
- user must be able to go back and change her input before final transaction will take place
- out-of-order and rogue request handling, introducing malformed state leading to invalid business transaction has to be prevented
- the testability of an application must not be compromised by implementation complexity

## Solution
State Machine is an established pattern to manage complex internal state flow based on a set of transition rules. 
See <https://brilliant.org/wiki/finite-state-machines/>.

In this library, you find a ready-to-use solution tailored for use in an HMRC-style frontend Play application, like `agent-invitations-frontend`. 

## Design
The key concept is a *Journey*. 
Each journey represents separate business transaction. 
It is only loosely related to the HTTP and user session, in fact, depending on the state persistence
implementation it can be a part of a user session or even it could span multiple users. 
Former is the common variant. It is expected of an application to have one or more journeys. 

Journey consist of a set of *State*s and *Transition*s. 

*State* can be anything but usually it will be a set of case classes/objects representing the stage and data of a business transaction. 
State is not expected to have finite values, can be continuous if needed!

*Transition* is a means of moving from one state to another. It is represented as a partial async function. 
Transition should be a *pure* function, depending only on its own parameters and state. 
External async requests to the upstream services should be provided as a function-type parameters. 

## Benefits
- proper concern separation: 
    - *model* defines core business transaction logic decoupled from the application implementation intricacies,
    - *controller* is responsible for wiring user interactions (HTTP requests and responses, HTML forms and pages) into the model,
    - *service* acts as glue between controller and model, providing persistence and session management.
- lightweight, complete and fast testing of a core journey model without spanning a Play application or an HTTP server.

## How-tos

### How to add play-fsm library to your service?

In your SBT build add:

    resolvers += Resolver.bintrayRepo("hmrc", "releases")
    
    libraryDependencies += "uk.gov.hmrc" %% "play-fsm" % "0.x.0-play-25"
    
or 
    
    libraryDependencies += "uk.gov.hmrc" %% "play-fsm" % "0.x.0-play-26"
    
### How to build a model?
- First, try to visualise user interacting with your application in any possible way. 
- Think about translating pages and forms into a diagram of states and transitions.
- Notice the required inputs and knowledge accumulated at each user journey stage.
- Create a new model object and define there the rules of the game, see an example in <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyModel.scala>.
- Create a unit test to validate all possible states and transitions outcomes, see an example in <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyModelSpec.scala>.

### How to persist in the state?
- play-fsm is not opinionated about state persistence and session management choice but provides an abstract API in the `PersistentJourneyService`.
- `JsonStateFormats` helps to encode/decode state to JSON when using MongoDB or an external REST service, e.g. <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyStateFormats.scala>.

### How to define a controller?
- Create a controller as usual extending `JourneyController` trait.
- Implement required abstract methods:
    - `getCallFor(state: State): Call` to translate state into matching GET endpoint url
    - `renderState(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]]): Request[_] => Result` to produce representation of a state, i.e. an HTML page
- Define actions using provided builder selection, see <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyController.scala>.

## Advanced examples:
- Agent Invitations: <https://github.com/hmrc/agent-invitations-frontend/tree/master/app/uk/gov/hmrc/agentinvitationsfrontend/journeys>
- Agent-Client relationships management help-desk: <TBC>

## Best practices
- Keep a single model definition in a single file.
- Name states as nouns and transitions as verbs.
- Carefully balance when to introduce new state and when to add properties to the existing one(s).
- Use a rule of thumb to keep only relevant data in the state.
- Try to avoid optional properties; their presence usually suggest splitting the state.
- Do NOT put functions and framework components in a state; a state should be immutable and serializable.
- Define transitions using curried methods. It works well with action builders.
- When the transition depends on some external operation(s), pass it as a function(s).
- GET actions should be idempotent, i.e. should only render existing or historical state.
- POST actions should always invoke some state transition and be followed be a redirect.
- Generate backlink using provided `getCallFor` and breadcrumbs head state if any.

## Common patterns

### State

- finite: sealed trait and a set of case classes/objects

```
    sealed trait State
    
    object State {
        case object Start extends State
        case class Continue(arg: String) extends State
        case class Stop(result: String) extends State
    }
```

- continuous: class or trait or a primitive value wrapper

```
    type State = String
```

### Transition

- simple transition depending only on a current state

```
val start = Transition {
    case State.Start        => goto(State.Start)
    case State.Continue(_)  => goto(State.Start)
    case State.Stop(_)      => goto(State.Stop)
}
```

- transition depending on a state and single parameter

```
def stop(user: User) = Transition {
    case Start              => goto(Stop(""))
    case Continue(value)    => goto(Stop(value))
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

