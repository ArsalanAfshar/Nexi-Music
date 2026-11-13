# Security policy

Please report vulnerabilities privately to the repository maintainers through GitHub's security advisory feature. Do not publish exploit details before a fix is available.

Preset files are untrusted input: the importer validates format/version and bounds archive entry sizes. Report any crash, path traversal, decompression-bomb or unexpected setting mutation. The app should not execute preset content.
