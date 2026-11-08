# BoxPhoBan

Phó bản theo phòng cho BoxPvP. Paper 1.21.1, Java 21.

- Bắt buộc: FastAsyncWorldEdit, MythicMobs 5.13
- Tuỳ chọn: PlaceholderAPI (scoreboard), Vault (tiền), BoxCore (gem), TAB
- Build: `./gradlew --offline build` ra `build/libs/BoxPhoBan-<version>.jar`
- `libs/` cần 2 jar không commit: `MythicMobs-5.13.0.jar` (copy từ `MyBoxMobs/libs`) và
  `FastAsyncWorldEdit-2.13.2.jar` (jar FAWE của server, đổi tên)
- Spec: [PHOBAN_SPEC.md](PHOBAN_SPEC.md)

## Cài đặt

1. Bỏ jar vào `plugins/`, khởi động server. Plugin tự tạo world void `boxphoban`.
2. Dán khối scoreboard trong [TAB_scoreboard_phoban.yml](TAB_scoreboard_phoban.yml) vào `plugins/TAB/config.yml`, `/tab reload`.
3. Sửa `config.yml` (whitelist lệnh, giới hạn phòng, thời gian chờ disconnect).

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

Người chơi `/pb`: `create`, `join`, `invite`, `accept`, `deny`, `kick`, `leader`, `public`, `start`, `leave`, `room`, `rewards`.

Admin `/pba`: `create`, `edit`, `exit`, `import`, `bounds`, `region`, `save`, `info`, `test`, `list`, `stop`, `watch`,
`ticket give`, `reward additem`, `loot additem`, `resetcooldown`, `delete`, `reload`.

Quyền: `boxphoban.use` (mặc định ai cũng có), `boxphoban.admin`, `boxphoban.bypass.commands`, `boxphoban.bypass.cooldown`,
`boxphoban.bypass.ticket`.

## Placeholder

`%boxphoban_in%` `%boxphoban_name%` `%boxphoban_difficulty%` `%boxphoban_stage%` `%boxphoban_stage_index%`
`%boxphoban_stage_total%` `%boxphoban_objective%` `%boxphoban_time_left%` `%boxphoban_time_elapsed%` `%boxphoban_lives%`
`%boxphoban_players_alive%` `%boxphoban_players%` `%boxphoban_mobs_left%` `%boxphoban_state%` `%boxphoban_in_room%`
`%boxphoban_room_dungeon%` `%boxphoban_cooldown_<id>%` `%boxphoban_clears_<id>%` `%boxphoban_best_<id>%`

## File

| File | Ai sửa | Nội dung |
|---|---|---|
| `config.yml` | admin | cài đặt chung |
| `messages.yml`, `gui.yml` | admin | chữ (key thiếu lấy mặc định trong jar) |
| `tickets.yml`, `rewards.yml`, `loot.yml` | admin, lệnh additem | vé, thưởng, loot |
| `dungeons/<id>/dungeon.yml` | admin | cài đặt phó bản, plugin không ghi đè |
| `dungeons/<id>/nodes.yml`, `map.schem` | editor | logic + map |
| `data/boxphoban.db` | plugin | lượt clear, best time, cooldown, kho thưởng chờ |
| `data/sessions/*.yml` | plugin | snapshot người chơi đang ở trong phó bản (xoá sau khi trả trạng thái) |
| `data/slots.yml` | plugin | ô đang dùng, để dọn sau khi server sập |

## Kiểm tra trong game (chưa test live)

- [ ] Tạo phòng, mời, đồng ý, kick, chuyển chủ phòng, công khai / riêng tư, đổi độ khó
- [ ] Vé CREATOR / EVERY_MEMBER / miễn phí; phí $; cooldown TIMER / DAILY; daily-limit
- [ ] Hết ô trống (giảm `limits.max-instances` về 1, mở 2 phòng)
- [ ] Paste map, người chơi vào điểm bắt đầu, scoreboard TAB hiện đúng
- [ ] Spawn mob theo độ khó (máu x, sát thương x), Dọn sạch đợt mở cửa, checkpoint
- [ ] Chết: giữ đồ, chờ hồi sinh rồi về checkpoint; hết mạng thành khán giả, không bay ra khỏi khung, không dùng menu spectator sang người ngoài
- [ ] `/pb leave` khi đang là khán giả: về đúng chỗ cũ, gamemode cũ
- [ ] Thoát game giữa chừng: vào lại trong thời gian chờ thì quay lại checkpoint; quá thời gian thì bị loại, lần sau vào được đưa về chỗ cũ
- [ ] Tắt server khi đang trong phó bản: lần sau vào được đưa về chỗ cũ, ô lưới được dọn khi khởi động
- [ ] Lệnh bị chặn (kể cả `/trade`), vứt đồ bị chặn, shift + chuột phải người chơi bị chặn
- [ ] Pet BoxPet đi theo, mob do pet giết tính cho chủ, pet ẩn khi chủ là khán giả
- [ ] Rương thưởng: loot riêng, chỉ nhận khi thắng; rời giữa chừng mất
- [ ] Thắng: thưởng + thưởng lần đầu + GUI kết quả, túi đầy thì vào `/pb rewards`
- [ ] `/pba watch`, `/pba stop`, `/pba list`
