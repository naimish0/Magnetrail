# Magnetrail release manifest template

Copy this file into a protected, immutable release record. Never add passwords, private keys, raw tester data, production config files, or service-account credentials.

```text
release_status: candidate | uploaded | promoted | halted | superseded
created_at_timezone:
release_owner:
approvers:
source_commit:
source_tree_clean: yes/no + approved exceptions
application_id: com.rameshta.magnetrail
version_code:
version_name:
min_sdk: 24
target_sdk: 36
compile_sdk: 37
gradle/agp/jdk:

aab_path:
aab_bytes:
aab_sha256:
aab_signing_state:
upload_certificate_sha256: fingerprint only
play_app_signing_certificate_sha256: fingerprint only
mapping_path_sha256:
baseline_profile_path_sha256:
native_symbols_path_sha256:
bundletool_version_sha256:
generated_apks_paths_sha256:

production_input_names_present: [names only]
firebase_project/app identifier: non-secret identifier approved for record
admob app/unit ownership verified: yes/no + console evidence link
privacy_policy_url:
support_url/email:
target_audience_decision:
data_safety/app_content approval:

automated_test_report:
device_qa_report:
16kb_evidence:
prelaunch_report:
closed_test_evidence:
known_issues/blockers:
rollout_decision/timestamp:
superseding_version/hotfix:
```

