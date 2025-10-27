# Changelog

All notable changes to Walnut will be documented here. Format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [OTF 1.1.0] - 2025-10-27

### Added

- Added debug option to OTFStandalone
- Added writeBA option to OTFStandalone
- Added threshold constant

### Fixed

- Fixed memory performance bug in InvertedIndex (potentially could have consumed 10x memory)
- Fixed AC Union performance bug (conservative AC unioning sometimes lost equivalence information); testing reveals occasional 10x speedups
- Fixed memory "leaks" in OTFCommandLine that used significant additional memory

### Changed

- Improved simulation relations generator; up to 2x faster
- Improved SmartBitSet manipulations
- Improved InvertedIndex overwrite operation
- Sorting of AC Unions by cardinality; sometimes 30% faster

### Removed

- Removed sanity-check option (it's superseded by writeBA option)

## [OTF 1.0.0] - 2025-05-12

### Added

- First checkin of OTF and OTFStandalone JARs
