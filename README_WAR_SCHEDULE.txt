War declaration schedule changes

Player /war declarations:
- Maximum 5 declarations per quota period.
- Quota reset at 13:00 Europe/Moscow.
- Wars are scheduled from the next calendar day, starting 17:00 Moscow,
  in 20-minute slots: 17:00, 17:20, 17:40, 18:00, 18:20.
- If a second quota period creates declarations whose next-day slots are already
  occupied, they are placed into the next available 5-slot date to avoid overlap.
- Scheduled start timestamps and declaration quota/reset time are saved in world data.
- Scheduled wars start at the scheduled time, bypassing the previous preparation countdown.

This is source-only. Build/test was not run in this environment.
