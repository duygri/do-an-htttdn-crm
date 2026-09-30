# Vietnam shipping address snapshot

Bundled on 2026-09-22; runtime never calls a third-party address API.

- Source: https://github.com/ThangLeQuoc/vietnamese-provinces-database
- Source file: `json/simplified_json_generated_data_vn_units.json`
- Source file revision: `15723a576946472d44ed3e81f2b89f652fd76885`
- Transformation: preserve `Code` and `FullName` as `code` and `name`, retain province → wards hierarchy; omit English, postal and other metadata.
- Snapshot: 34 province-level units and 3,321 commune-level units (including special zones).
- Official reference: https://baochinhphu.vn/bang-danh-muc-va-ma-so-cua-34-tinh-thanh-moi-3321-don-vi-hanh-chinh-cap-xa-moi-102250704153652947.htm

The official reference establishes the two-level directory and counts under Decision 19/2025/QĐ-TTg. This is a community-maintained machine-readable transcription, not an official government API. Counts and selected names/codes are checked; this is not a legal certification of every record or an automatic legacy-boundary conversion.

When updating, obtain the source revision, retain its license, compare codes/names with the official directory, run data integrity and shipping tests, and update this record. Do not automatically map legacy districts or split/merged wards: unmatched saved addresses require customer selection.
