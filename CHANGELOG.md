# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
### Added
- qr scanner to import projects
[#20](https://gitlab.com/eneiluj/moneybuster/issues/20) @denics
- web link in sharing dialog
[cospend#42](https://gitlab.com/eneiluj/cospend-nc/issues/42) @jreybert

### Changed
- settlement algorithm: use the same as Cospend and IHateMoney (from https://framagit.org/almet/debts)
[#15](https://gitlab.com/eneiluj/moneybuster/issues/15) @nicocool84
- improve CI script, now can sign release apk

### Fixed
- hide 'create on server' when importing a project from QR/url

## 0.0.11 – 2019-07-12
### Added
- ability to manipulate recurring bills (cospend projects only)
[!6](https://gitlab.com/eneiluj/moneybuster/merge_requests/6) @AndyScherzinger
- avatar in bill edition
[!10](https://gitlab.com/eneiluj/moneybuster/merge_requests/10) @AndyScherzinger

### Changed
- change project type icon in new project form
[!7](https://gitlab.com/eneiluj/moneybuster/merge_requests/7) @AndyScherzinger
- respect locale when formatting dates
[!9](https://gitlab.com/eneiluj/moneybuster/merge_requests/9) @AndyScherzinger and @eneiluj
- update cert4android and dependencies

### Fixed
- trim project URL
[!8](https://gitlab.com/eneiluj/moneybuster/merge_requests/8) @AndyScherzinger
- crash when validating certificate

## 0.0.10 – 2019-07-04
### Added

### Changed
- settlement: sort members like IHateMoney does to get same results
[#15](https://gitlab.com/eneiluj/moneybuster/issues/15) @nicocool84
- show/hide all/none buttons dynamically
- UI improvements in sidebar
- show bill edition validate button only if something changed and values are valid

### Fixed
- negative number rounding
[#14](https://gitlab.com/eneiluj/moneybuster/issues/14) @jeisonp
- apply new theme/color immediately
- disable ability to refresh list layout when no network connectivity
- bill list item layout are now displayed correctly with big font size
[#17](https://gitlab.com/eneiluj/moneybuster/issues/17) @Aldarone

## 0.0.9 – 2019-05-08
### Added

### Changed
- improve bill edition form design
- improve bill list items
- remove bill info dialog
- improve settlement/stats dialogs design
- improve settings theme icon

### Fixed
- use our own icons instead of system ones (which can change)
- prevent fields autofill
- don't show sync icon for local projects
[#13](https://gitlab.com/eneiluj/moneybuster/issues/13) @Nuntius0
- remove duplicated ways to validate/delete/cancel in bill edition

## 0.0.8 – 2019-05-04
### Added
- FAB button to save bill
[#10](https://gitlab.com/eneiluj/moneybuster/issues/10) @Nuntius0

### Changed
- improve keyboard/selection behaviour in forms/preferences screens
[#11](https://gitlab.com/eneiluj/moneybuster/issues/11) @Nuntius0

### Fixed
- apply color change after backpressed from preferences

## 0.0.7 – 2019-04-24
### Added
- ability to select all/none bill owers in bill edition
- remember project last payer and use it for new bills
[#9](https://gitlab.com/eneiluj/moneybuster/issues/9) @zonque

### Changed
- dark theme: real black
- click on current project name label => select project dialog
- bill edition form is now a...form
[#9](https://gitlab.com/eneiluj/moneybuster/issues/9) @zonque
- dev flavout icon color: orange

### Fixed
- accept comas for member weight and bill amount
[#5](https://gitlab.com/eneiluj/moneybuster/issues/5) @polkillas1
- bill list background
- black theme issues with black as app main color
- reduce top sidebar part height
[#7](https://gitlab.com/eneiluj/moneybuster/issues/7) @Obigre
- project sync on startup

## 0.0.6 – 2019-03-09
### Added
- translations

### Changed
- use different app flavours for F-Droid and CI builds to be able to install both versions in parallel

## 0.0.5 – 2019-03-08
### Added
- able to generate QRCode to share a project to another phone with MoneyBuster installed
- able to catch VIEW intent when a "moneybuster" link is visited to import a project

### Changed
- bump gradle plugin version
- allow negative bill amount values
[#4](https://gitlab.com/eneiluj/moneybuster/issues/4) @Michael-Hofer

### Fixed
- declare server address field as textURI
[#3](https://gitlab.com/eneiluj/moneybuster/issues/3) @Salamandar

## 0.0.4 – 2019-03-01
### Added
- new option to set app main color
- now able to receive VIEW attempts for URI like cospend://my.nextcloud.org/projectid

### Changed
- get rid of butterknife
- bump to androidx
- update cert4android
- CI : keep debug apk only

## 0.0.3 – 2019-02-15
### Added

### Changed
- moved floating buttons
- better default project URL, type-specific
- payback -> cospend

### Fixed
- use compat alert dialog
- huge bug when searching default URL for new project

## 0.0.2 – 2019-02-08
### Added
- able to sync with Nextcloud Payback
- source svg icon

### Changed
- a few icons

### Fixed
- stop animation when create project fails
- sort member correctly
- sort members for settlement to get same results than Payback

## 0.0.1 – 2019-01-23
### Added
- new app !

### Fixed
- the world
