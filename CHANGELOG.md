# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]
## 0.0.18 – 2020-03-10
### Added
- optional periodical sync service with otpional notifications
- bank transfer payment mode
- ability to export projects to CSV files
- ability to import CSV files to local project

### Changed
- local projects can now use category and payment mode
- min Android version is now 5 (API 21, Lollipop)

### Fixed
- balance check when adding member to sidebar
- gplay complaints
[#32](https://gitlab.com/eneiluj/moneybuster/issues/32) @AndyScherzinger
[#33](https://gitlab.com/eneiluj/moneybuster/issues/33) @AndyScherzinger

## 0.0.17 – 2020-01-23
### Added
- custom currencies support
- select dialog to choose what to do when no project/account
- get NC color if account configured, optionnaly use it as main app color

### Changed
- disabled avatar
- updated screenshots (english/french)
- default app color is nextcloudish blue
- updated AUTHORS

### Fixed
- coherence between Cospend and MoneyBuster behaviour regarding disabled members

## 0.0.16 – 2020-01-15
### Added
- now able to access projects with Nextcloud credentials
- automatically add projects from Nextcloud account
- 'reimbursement' bill category
- show custom categories (Cospend >= 0.3.2)
- member color edition
[#18](https://gitlab.com/eneiluj/moneybuster/issues/18) @nicoe

### Changed
- new optional way of syncing with Cospend, just get what's newer than last sync
- show category/payment mode icons in stat filters and bill edition form
[Cospend#58](https://gitlab.com/eneiluj/cospend-nc/issues/58) @archit3kt
- launcher icons
- show member avatars in sidebar

### Fixed
- old date dialog (on some Android versions) closing on click
[#29](https://gitlab.com/eneiluj/moneybuster/issues/29) @almereyda
- write avatar letter in black if color is too bright

## 0.0.15 – 2019-11-03
### Added
- now able to change local password
- use member colors from Cospend if possible
- search by name with prefixes (+payer -ower @payer-OR-ower)
[#22](https://gitlab.com/eneiluj/moneybuster/issues/22) @patxiku
- help dialog explaining how to search

### Changed
- improve new project URL check, add https:// prefix if absent
[#51](https://gitlab.com/eneiluj/cospend-nc/issues/51) @doronbehar
- red background for invalid fields in new project form
[#51](https://gitlab.com/eneiluj/cospend-nc/issues/51) @eneiluj
- instantly add project after scanning/browsing valid link
[#51](https://gitlab.com/eneiluj/cospend-nc/issues/51) @eneiluj
- give focus to password field when scanning/browsing link with no password
[#51](https://gitlab.com/eneiluj/cospend-nc/issues/51) @eneiluj
- select member in sidebar: show bill involving this member as payer OR ower
[#22](https://gitlab.com/eneiluj/moneybuster/issues/22) @patxiku

### Fixed
- avoid having server URL overriden by default URL after scanning a QRCode
- fix link browsing project type choice
- balance color bug with number ending with 0.00
[#22](https://gitlab.com/eneiluj/moneybuster/issues/22) @patxiku

## 0.0.14 – 2019-10-19
### Added
- search by payer name
[#22](https://gitlab.com/eneiluj/moneybuster/issues/22) @patxiku
- lots of german and italian translations

### Fixed
- sync new cospend bill, forgot to put paymentmode and category parameters

## 0.0.13 – 2019-10-13
### Added
- new categories
- filters in project statistics
- button to select all bills in selection mode
[#24](https://gitlab.com/eneiluj/moneybuster/issues/24) @patxiku
[#23](https://gitlab.com/eneiluj/moneybuster/issues/23) @patxiku
- now search by ower name
[#22](https://gitlab.com/eneiluj/moneybuster/issues/22) @patxiku
- privacy policy
- automatic settlement bills creation
[#27](https://gitlab.com/eneiluj/moneybuster/issues/27) @leoossa

### Fixed
- project name when sharing stats and settlement for local project
- hide 'share project' button for local projects
[#25](https://gitlab.com/eneiluj/moneybuster/issues/25) @PEPERSO
- bill selection
- category and payment mode were not set for new bills
- bump SSO lib to 0.4.1, now working with Nextcloud dev accounts

## 0.0.12 – 2019-09-14
### Added
- qr scanner to import projects
[#20](https://gitlab.com/eneiluj/moneybuster/issues/20) @denics
- web link in sharing dialog
[cospend#42](https://gitlab.com/eneiluj/cospend-nc/issues/42) @jreybert
- show total payed in stats dialog
- able to catch BROWSE intent for ihatemoney.org URLs
[#21](https://gitlab.com/eneiluj/moneybuster/issues/21) @eMerzh
- offline mode to only sync when user asks
[#21](https://gitlab.com/eneiluj/moneybuster/issues/21) @eMerzh
- Nextcloud account settings to be able to import Cospend projects from there
[#21](https://gitlab.com/eneiluj/moneybuster/issues/21) @eMerzh
[#20](https://gitlab.com/eneiluj/moneybuster/issues/20) @denics
- tooltip for FAB
[#21](https://gitlab.com/eneiluj/moneybuster/issues/21) @eMerzh
- welcome dialogs (one first and one for each release)
[#21](https://gitlab.com/eneiluj/moneybuster/issues/21) @eneiluj
- show bill payment mode and category for cospend projects

### Changed
- settlement algorithm: use the same as Cospend and IHateMoney (from https://framagit.org/almet/debts)
[#15](https://gitlab.com/eneiluj/moneybuster/issues/15) @nicocool84
- improve CI script, now can sign release apk
- tap on project name when there is none => create one
[#21](https://gitlab.com/eneiluj/moneybuster/issues/21) @eMerzh
- make sync success toast more discreet
- "new project" screen is now a more intuitive form
[#21](https://gitlab.com/eneiluj/moneybuster/issues/21) @eMerzh
- able to scan QRCode from "new project" screen
[#21](https://gitlab.com/eneiluj/moneybuster/issues/21) @eMerzh
- move many buttons, make add-project/share/settle/stats more visible
[#21](https://gitlab.com/eneiluj/moneybuster/issues/21) @eMerzh

### Fixed
- hide useless buttons when there is no project
- owner avatar display in bill edition for small screens

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
