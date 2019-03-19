# play-fsm

This library provides Finite State Machine building blocks for a stateful Play application.

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

Finite State Machine is an established pattern to manage complex internal state flow based on a set of transition rules. 
See <https://brilliant.org/wiki/finite-state-machines/>.

In this library, you find a ready-to-use solution tailored for use in HMRC-style frontend Play application.

## How-tos

### How to add play-fsm library to your service?

In your SBT build add:

    resolvers += Resolver.bintrayRepo("hmrc", "releases")
    
    libraryDependencies += "uk.gov.hmrc" %% "play-fsm" % "0.2.0-play-25"
    
### How to build a process model?
- First, try to visualise user interacting with your application in any possible way. 
- Think about translating pages and forms into a diagram of states and transitions.
- Notice the required inputs and knowledge accumulated at each user journey stage.
- Create a new model object and define there the rules of the game, see an example in <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyModel.scala>.
- Create a unit test to validate all possible states and transitions outcomes, see an example in <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyModelSpec.scala>.

### How to persist in the state?
- play-fsm is not opinionated about state persistence choice but provides an abstract API in the `PersistentJourneyService`.
- `JsonStateFormats` helper is provided to quickly encode/decode JSON when storing state in a MongoDB or in an external service, e.g. <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyStateFormats.scala>.

### How to define a controller?
- Create a controller as usual extending `JourneyController` trait.
- Implement 2 required abstract methods:
- - `getCallFor(state: State): Call` to translate state into matching GET endpoint url
- - `renderState(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]]): Request[_] => Result` to produce representation of a state, i.e. an HTML page
- Define actions using provided builder selection, see <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyController.scala>.

## Advanced examples:
- Agent Invitations: <https://github.com/hmrc/agent-invitations-frontend/tree/master/app/uk/gov/hmrc/agentinvitationsfrontend/journeys>
- Agent-Client relationships management help-desk: <TBC>

## Best practices
- Keep a single model definition in a single file.
- Name states as nouns and transitions as verbs.
- Carefully balance when to introduce new state and when to add properties to the existing one(s).
- Use a rule of thumb to keep only relevant information in the state.
- Try to avoid optional properties; their presence usually suggest splitting the state.
- Do NOT inject functions in a state; a state should be immutable and serializable.
- Define transitions using curried methods. It works well with action builders.
- When the transition depends on some external operation(s), pass it as a function(s).
- GET actions should be idempotent, i.e. should only render existing or historical state.
- POST actions should always invoke some state transition and be followed be redirect.
- Generate backlinks using provided `getCallFor` and breadcrumbs head if any.


