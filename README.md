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
    
### How to start building your FSM journey?

- First, try to visualise user interacting with your application in any possible way. 
- Think about translating pages and forms into a diagram of states and transitions.
- Notice the required inputs and knowledge accumulated at each user journey stage.
- Create a new model object and define there the rules of the game, see <https://github.com/hmrc/play-fsm/blob/master/src/test/scala/uk/gov/hmrc/play/fsm/DummyJourneyModel.scala>
- Create a unit test to validate all possible states and transitions outcomes.



