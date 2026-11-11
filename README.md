# BoxPhoBan

Phó bản theo phòng cho server BoxPvP. Người chơi lập phòng, rủ bạn, chọn độ khó rồi vào một bản sao riêng của map để
qua từng ải: đánh quái MythicMobs, giữ vị trí, tìm chìa mở cổng, đánh boss. Admin dựng map và toàn bộ logic ngay trong
game, không cần viết script.

Paper 1.21.1, Java 21. Cần MythicMobs 5.13 và FastAsyncWorldEdit. PlaceholderAPI (scoreboard), Vault (phí vào),
BoxCore (Gem), DecentHolograms (holo giữ vị trí) và BoxPet (tính kill của pet) không bắt buộc.

## Người chơi thấy gì

- `/pb` mở danh sách phó bản. Tạo phòng, mời người hoặc mở công khai, chủ phòng chọn độ khó rồi bắt đầu.
- Mỗi phòng chạy trên một ô riêng trong world void `boxphoban` (plugin tự tạo), map được paste từ bản gốc mỗi lượt.
- Điều kiện vào đặt theo từng phó bản: vé (chủ phòng trả hoặc mỗi người trả), phí $, quyền, phải clear phó bản khác
  trước, cooldown (theo phút, ngày hoặc tuần), giới hạn lượt mỗi ngày.
- Mạng chung cả phòng, mạng riêng hoặc không có mạng. Chết thì giữ đồ, chờ rồi hồi sinh ở checkpoint; hết mạng thành
  khán giả trong phạm vi map.
- Độ khó đổi level MythicMobs, nhân máu / sát thương của quái, giới hạn thời gian và bảng thưởng.
- Trong phó bản: chặn lệnh ngoài whitelist, chặn vứt đồ, PvP, mở rương lạ và tương tác người chơi.
- Thoát game giữa chừng thì có `disconnect.grace-seconds` để vào lại đúng checkpoint. Server sập thì lần sau vào được
  đưa về chỗ cũ và trạng thái cũ; ô lưới bị bỏ dở được dọn khi khởi động.
- Thưởng chưa nhận được (túi đầy) nằm trong `/pb rewards`.

## Cài đặt

1. Bỏ jar vào `plugins/`, khởi động server.
2. Thêm scoreboard phó bản vào TAB bằng các placeholder `%boxphoban_...%` bên dưới.
3. Sửa `config.yml`: whitelist lệnh, số phòng tối đa (`limits.max-instances`), thời gian chờ khi thoát game.

## Dựng một phó bản

1. `/pba create hamngucxuong`: tạo phó bản và đưa bạn vào ô editor (creative, có 4 công cụ trên hotbar).
2. Dựng map tại đó, hoặc chọn một map có sẵn bằng WorldEdit (`//pos1`, `//pos2`) rồi `/pba import hamngucxuong`.
   Import tự đặt khung map. Nếu dựng tay: chọn 2 góc bằng rìu vàng rồi `/pba bounds`.
3. La bàn (chuột phải):
   - Điểm bắt đầu: đứng ở chỗ muốn người chơi xuất hiện rồi bấm.
   - Thêm điểm: checkpoint, điểm teleport (đặt tên, ví dụ `cp1`).
   - Ải: thêm ải theo thứ tự, mỗi ải có tên + dòng mục tiêu (dùng được `%mobs_left%`, `%time_left%`, `%lives%`).
     Ải đầu tiên tự bắt đầu khi vào phó bản.
4. Rìu vàng chọn 2 góc rồi `/pba region add cua1`: vùng cho cửa hoặc trigger "Vào vùng".
5. Lông vũ chuột phải vào block: tạo node, chọn trigger, thêm điều kiện, thêm hành động.
6. `/pba save`: lưu map. Mọi thay đổi node/điểm/vùng/ải đã tự lưu vào `nodes.yml`.
7. Sửa `plugins/BoxPhoBan/dungeons/hamngucxuong/dungeon.yml`: tên, mô tả, số người, vé, cooldown, mạng, độ khó.
   Đặt `enabled: true` rồi `/pba reload`.
8. Tạo bảng thưởng trong `rewards.yml` đúng tên `difficulties.<id>.rewards` (mặc định `<id phó bản>_<độ khó>`).
9. `/pba info` kiểm tra, `/pba test hamngucxuong kho` chơi thử một mình.

### Ví dụ logic một ải

| Node | Trigger | Hành động |
|---|---|---|
| n1 (giữa phòng 1) | Bắt đầu ải `vao` | Spawn mob `Skeleton_Guard` x5 |
| n2 | Dọn sạch đợt, node `n1` | Mở cửa `cua1`, Đặt checkpoint `cp1`, Chuyển ải `next` |
| n3 (phòng boss) | Vào vùng `phongboss`, players `all` | Đóng cửa `cua2`, Spawn boss `Bone_King` |
| n4 | Giết mob `Bone_King` x1 | Hoàn thành |
| n5 (rương) | Tương tác, bật "Tính riêng từng người" | Rương thưởng `vidu_ruong` |

### Phòng khoá (đánh hết quái mới đi tiếp)

1. Dựng tường ở cửa vào và cửa ra **ở vị trí đóng** (bịt kín lối), chọn bằng rìu vàng:
   `/pba region add vao2`, `/pba region add ra2`. Chọn cả phòng: `/pba region add phong2`.
2. Node A (sàn trong phòng): trigger **Vào vùng** `region = phong2`, `players = 1` →
   **Khoá phòng** `room = phong2`, `entrances = vao2`, `exits = ra2`, `style = down|up|side`, `seconds = 2` →
   **Chuyển ải** `phong2`.
3. Node B: trigger **Phòng khoá kín** `room = phong2` → **Spawn mob** (thêm node spawn khác nếu muốn nhiều chỗ).
4. Node C (tuỳ chọn): trigger **Dọn sạch ải** → **Thông báo** title, **Đặt checkpoint**...
   Cửa vào và cửa ra tự mở khi dọn sạch ải.

Khi phó bản bắt đầu, các tường này tự được gỡ nên lối đi rộng như map gốc. Người chết trong phòng hồi sinh ở checkpoint
bên ngoài và chờ đội đánh xong. Nếu không còn ai sống trong phòng, cửa vào mở lại để vào đánh tiếp (quái giữ nguyên).
Tường muốn đóng sẵn từ đầu: `/pba region wall <vùng> closed`.

### Rương chìa đổi chỗ mỗi lượt

Đặt rương ở nhiều chỗ, mỗi rương là một node (trigger **Tương tác** → **Cho chìa**). Ở ô **Nhóm ngẫu nhiên** của từng
node gõ cùng một tên, ví dụ `ruongchia` (hoặc `ruongchia:2` để giữ 2 rương). Mỗi lượt plugin giữ ngẫu nhiên số rương đó,
các node còn lại bị tắt và rương của chúng bị gỡ khỏi map (chuột phải ô nhóm để tắt việc gỡ block).

### Giữ vị trí

Trigger **Giữ vị trí** `region`, `seconds`, `players` (tối thiểu), `mode = reset` (rời vùng là về 0) hoặc `pause`.
Viền hạt quanh vùng, holo DecentHolograms phía trên (tên + thanh % ), actionbar và scoreboard hiện tiến độ.
Bật tắt chung trong `config.yml` (`hold.*`), từng node bằng thông số `hologram` / `border`, chữ trong `messages.yml` (`hold.*`). Nên thêm điều kiện **Ải hiện tại** để vùng chỉ hoạt động trong ải đó.

### Cổng cần chìa khoá

1. Xây cổng ở vị trí đóng, `/pba region add cong1`, rồi `/pba region wall cong1 closed` (cổng đóng sẵn từ đầu).
2. Lấy chìa, chọn một trong hai:
   - Quái giữ chìa: node trigger **Giết mob** `mob = <id quái giữ chìa>` → **Cho chìa** `key = chia1`, `who = trigger`
     (người hạ quái nhận chìa, kể cả khi pet hạ).
   - Rương có chìa: node đặt trên block rương, trigger **Tương tác** → **Cho chìa** `key = chia1`, `who = trigger`.
3. Node mở cổng: trigger **Dùng chìa** `key = chia1`, `region = cong1` (bấm block nào của cổng cũng được) →
   **Mở tường** `region = cong1`, `style = up|down|side`.

Chìa chỉ dùng được trong đúng lượt phó bản đó (mỗi lượt có mã riêng), không vứt ra được, và bị gỡ khỏi túi khi rời,
thua, thắng, hết giờ, thoát game hay server sập.

## Lệnh

| Lệnh | Mô tả |
|---|---|
| `/pb` | Danh sách phó bản |
| `/pb create`, `/pb join`, `/pb room` | Tạo phòng, vào phòng, mở menu phòng |
| `/pb invite <người>`, `/pb accept`, `/pb deny` | Mời, nhận, từ chối lời mời |
| `/pb kick <người>`, `/pb leader <người>`, `/pb public` | Chủ phòng: kick, chuyển chủ, bật / tắt công khai |
| `/pb start`, `/pb leave` | Bắt đầu, rời phòng hoặc phó bản |
| `/pb rewards` | Kho thưởng chờ nhận |
| `/pba create\|edit <id>`, `/pba exit` | Tạo, vào, rời editor |
| `/pba import <id>`, `/pba bounds`, `/pba pos`, `/pba save` | Lấy map từ vùng chọn WorldEdit, đặt khung, lưu map |
| `/pba region add\|remove <tên>`, `/pba region wall <vùng> open\|closed` | Vùng cho cửa và trigger |
| `/pba info <id>`, `/pba list`, `/pba test <id> <độ khó>` | Kiểm tra, liệt kê, chơi thử một mình |
| `/pba stop`, `/pba watch` | Dừng một phòng đang chạy, vào xem |
| `/pba ticket give`, `/pba reward additem`, `/pba loot additem` | Vé, thêm item đang cầm vào bảng thưởng / loot |
| `/pba resetcooldown`, `/pba delete <id>`, `/pba reload` | Xoá cooldown, xoá phó bản, đọc lại file |

Quyền: `boxphoban.use` (mọi người), `boxphoban.admin` (op), `boxphoban.bypass.commands`, `boxphoban.bypass.cooldown`,
`boxphoban.bypass.ticket`.

## Placeholder

| Placeholder | Giá trị |
|---|---|
| `%boxphoban_in%`, `%boxphoban_state%`, `%boxphoban_in_room%`, `%boxphoban_room_dungeon%` | Đang ở trong phó bản / phòng nào |
| `%boxphoban_name%`, `%boxphoban_difficulty%` | Tên phó bản, độ khó |
| `%boxphoban_stage%`, `%boxphoban_stage_index%`, `%boxphoban_stage_total%`, `%boxphoban_objective%` | Ải hiện tại và mục tiêu |
| `%boxphoban_time_left%`, `%boxphoban_time_elapsed%` | Thời gian |
| `%boxphoban_lives%`, `%boxphoban_players_alive%`, `%boxphoban_players%`, `%boxphoban_mobs_left%` | Mạng, người còn sống, quái còn lại |
| `%boxphoban_hold%`, `%boxphoban_hold_percent%`, `%boxphoban_hold_bar%`, `%boxphoban_hold_name%` | Tiến độ giữ vị trí |
| `%boxphoban_cooldown_<id>%`, `%boxphoban_clears_<id>%`, `%boxphoban_best_<id>%` | Cooldown, số lần clear, thời gian tốt nhất |

## File

| File | Ai sửa | Nội dung |
|---|---|---|
| `config.yml` | admin | Cài đặt chung |
| `messages.yml`, `gui.yml` | admin | Chữ (khoá thiếu lấy mặc định trong jar) |
| `tickets.yml`, `rewards.yml`, `loot.yml` | admin, lệnh additem | Vé, thưởng, loot |
| `dungeons/<id>/dungeon.yml` | admin | Cài đặt phó bản, plugin không ghi đè |
| `dungeons/<id>/nodes.yml`, `map.schem` | editor | Logic và map |
| `data/boxphoban.db` | plugin | Lượt clear, thời gian tốt nhất, cooldown, thưởng chờ |
| `data/sessions/*.yml` | plugin | Trạng thái người chơi đang trong phó bản, xoá sau khi trả lại |
| `data/slots.yml` | plugin | Ô đang dùng, để dọn sau khi server sập |

## Build

```
./gradlew build
```

Ra file `build/libs/BoxPhoBan-<version>.jar`. Repo chỉ chứa mã nguồn, không có jar nào ngoài
`gradle/wrapper/gradle-wrapper.jar` của Gradle. Trước khi build, đặt vào thư mục `libs/`:

- `MythicMobs-5.13.0.jar`: jar plugin MythicMobs (bản free)
- `FastAsyncWorldEdit-2.13.2.jar`: jar plugin FastAsyncWorldEdit (đổi tên cho khớp)
- `DecentHolograms-2.10.1.jar`: jar plugin DecentHolograms
- `boxcore-api-1.4.0.jar`: build từ [BoxCore](https://github.com/FLD-TN/BoxCore) (`boxcore-api/build/libs/`)
