# Changelog

All notable changes to Walnut will be documented here. Format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [OTF 1.2.0] - 2026-11-02

### Fixed

- Fix XOR staleness bug; although this pathway wasn't used and so doesn't affect current behavior, it was a true correctness issue.
- Fixed AC Union performance bug (some ACs were not merged with their primary element); testing reveals an average 5% speedup, although in rare cases could be much higher

### Changed

Improved BitSet-to-SmartBitSet conversion memory usage

## [OTF 1.1.0] - 2025-10-29

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
