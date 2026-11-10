# Asset credits

Fit Nova 1.5 bundles the open-source assets below. The full licence notices ship inside the app in
`app/src/main/assets/licenses.txt` (Profile → Open-source licences).

| Asset | Used as | Licence | Source |
|---|---|---|---|
| Phosphor Icons 2.1.1 (regular, duotone, fill) | `res/drawable/ic_*.xml` | MIT, © 2023 Phosphor Icons | [phosphor-icons/core](https://github.com/phosphor-icons/core) |
| Fluent Emoji, Flat style | `res/drawable/emoji_*.xml` | MIT, © Microsoft Corporation | [microsoft/fluentui-emoji](https://github.com/microsoft/fluentui-emoji) |
| Open Doodles by Pablo Stanley | `illu_welcome`, `illu_camera_coach`, `illu_celebrate`, `illu_no_workouts`, `illu_offline`, `illu_podium`, `illu_routine`, `illu_sleep` | CC0 1.0 | [opendoodles.com](https://www.opendoodles.com) |
| GitLab SVGs, Pajamas spot illustrations | `illu_alarm_morning`, `illu_cloud_sync`, `illu_groups_empty`, `illu_history_empty`, `illu_sign_in`, `illu_stats` | MIT, © 2011-present GitLab Inc. | [gitlab-org/gitlab-svgs](https://gitlab.com/gitlab-org/gitlab-svgs) |
| Manrope (variable) | `res/font/manrope.ttf` | SIL OFL 1.1, © 2018 The Manrope Project Authors | [googlefonts/manrope](https://github.com/googlefonts/manrope) |
| Exercise photos | `assets/exercise_photos/` | Public domain, CC0, CC BY, CC BY-SA | [PHOTO_CREDITS_EXERCISES.md](PHOTO_CREDITS_EXERCISES.md) |
| Recipe photos | `assets/recipe_photos/` | CC BY, CC BY-SA and others | [PHOTO_CREDITS.md](PHOTO_CREDITS.md) |

## Changes made

- Icons, emoji and illustrations were converted from SVG to Android vector drawables. Paths are unchanged.
- Illustrations were recoloured to the Nova Indigo palette, with a light (`illu_<scene>`) and a dark
  (`illu_<scene>_dark`) copy. Transparent no-op strokes were removed, and the cloud-sync spot uses
  1-unit strokes so it matches the other spots.
- The back, arrow-right and chevron icons are marked `autoMirrored` so they flip in right-to-left layouts.
- The launcher icon is Fit Nova's own artwork.
