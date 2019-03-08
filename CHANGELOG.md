# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

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
