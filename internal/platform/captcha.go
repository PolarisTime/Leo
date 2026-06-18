package platform

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"image"
	"image/color"
	"image/draw"
	"image/png"
	"math/big"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
)

const (
	captchaPrefix      = "auth:captcha:"
	captchaTTL         = 5 * time.Minute
	captchaChars       = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
	captchaCharCount   = 4
	captchaImageWidth  = 130
	captchaImageHeight = 48
	loginCaptchaSwitch = "SYS_LOGIN_CAPTCHA"
)

type CaptchaService struct {
	db    *pgxpool.Pool
	redis *redis.Client
}

type Captcha struct {
	CaptchaID    string
	CaptchaImage string
	Required     bool
}

func NewCaptchaService(db *pgxpool.Pool, redis *redis.Client) CaptchaService {
	return CaptchaService{db: db, redis: redis}
}

func (s CaptchaService) Generate(ctx context.Context) (Captcha, error) {
	code, err := randomCode(captchaCharCount)
	if err != nil {
		return Captcha{}, err
	}
	captchaID, err := randomUUID()
	if err != nil {
		return Captcha{}, err
	}
	if s.redis == nil {
		return Captcha{}, fmt.Errorf("redis client is not configured")
	}
	if err := s.redis.Set(ctx, captchaPrefix+captchaID, code, captchaTTL).Err(); err != nil {
		return Captcha{}, err
	}
	required, err := s.shouldRequireLoginCaptcha(ctx)
	if err != nil {
		return Captcha{}, err
	}
	imageText, err := renderCaptchaPNG(code)
	if err != nil {
		return Captcha{}, err
	}
	return Captcha{
		CaptchaID:    captchaID,
		CaptchaImage: imageText,
		Required:     required,
	}, nil
}

func (s CaptchaService) ShouldRequireLoginCaptcha(ctx context.Context) (bool, error) {
	return s.shouldRequireLoginCaptcha(ctx)
}

func (s CaptchaService) Verify(ctx context.Context, captchaID string, inputCode string) (bool, error) {
	captchaID = strings.TrimSpace(captchaID)
	inputCode = strings.TrimSpace(inputCode)
	if captchaID == "" || inputCode == "" {
		return false, nil
	}
	if s.redis == nil {
		return false, fmt.Errorf("redis client is not configured")
	}
	key := captchaPrefix + captchaID
	stored, err := s.redis.Get(ctx, key).Result()
	if err != nil {
		if err == redis.Nil {
			return false, nil
		}
		return false, err
	}
	if err := s.redis.Del(ctx, key).Err(); err != nil {
		return false, err
	}
	return strings.EqualFold(stored, inputCode), nil
}

func (s CaptchaService) shouldRequireLoginCaptcha(ctx context.Context) (bool, error) {
	if s.db == nil {
		return false, nil
	}
	var enabled bool
	err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_no_rule
			 WHERE setting_code = $1
			   AND status = '正常'
			   AND deleted_flag = false
		)
	`, loginCaptchaSwitch).Scan(&enabled)
	return enabled, err
}

func randomCode(length int) (string, error) {
	var builder strings.Builder
	builder.Grow(length)
	max := big.NewInt(int64(len(captchaChars)))
	for i := 0; i < length; i++ {
		index, err := rand.Int(rand.Reader, max)
		if err != nil {
			return "", err
		}
		builder.WriteByte(captchaChars[index.Int64()])
	}
	return builder.String(), nil
}

func randomUUID() (string, error) {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return "", err
	}
	raw[6] = (raw[6] & 0x0f) | 0x40
	raw[8] = (raw[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", raw[0:4], raw[4:6], raw[6:8], raw[8:10], raw[10:16]), nil
}

func renderCaptchaPNG(code string) (string, error) {
	img := image.NewRGBA(image.Rect(0, 0, captchaImageWidth, captchaImageHeight))
	draw.Draw(img, img.Bounds(), image.NewUniform(color.White), image.Point{}, draw.Src)

	for i := 0; i < 60; i++ {
		x, err := randomInt(captchaImageWidth)
		if err != nil {
			return "", err
		}
		y, err := randomInt(captchaImageHeight)
		if err != nil {
			return "", err
		}
		setBlock(img, x, y, color.RGBA{R: 200, G: 200, B: 200, A: 255})
	}

	for i, char := range code {
		x := 18 + i*28
		y := 12
		drawGlyph(img, x, y, byte(char), color.RGBA{R: 30, G: 90, B: 140, A: 255})
	}

	var buffer bytes.Buffer
	if err := png.Encode(&buffer, img); err != nil {
		return "", err
	}
	return "data:image/png;base64," + base64.StdEncoding.EncodeToString(buffer.Bytes()), nil
}

func randomInt(max int) (int, error) {
	value, err := rand.Int(rand.Reader, big.NewInt(int64(max)))
	if err != nil {
		return 0, err
	}
	return int(value.Int64()), nil
}

func setBlock(img *image.RGBA, x int, y int, c color.Color) {
	for dx := 0; dx < 2; dx++ {
		for dy := 0; dy < 2; dy++ {
			px := x + dx
			py := y + dy
			if image.Pt(px, py).In(img.Bounds()) {
				img.Set(px, py, c)
			}
		}
	}
}

func drawGlyph(img *image.RGBA, x int, y int, char byte, c color.Color) {
	glyph, ok := captchaGlyphs[char]
	if !ok {
		glyph = captchaGlyphs['?']
	}
	for row, bits := range glyph {
		for col, bit := range bits {
			if bit == '1' {
				setScaledPixel(img, x+col*3, y+row*3, c)
			}
		}
	}
}

func setScaledPixel(img *image.RGBA, x int, y int, c color.Color) {
	for dx := 0; dx < 3; dx++ {
		for dy := 0; dy < 3; dy++ {
			px := x + dx
			py := y + dy
			if image.Pt(px, py).In(img.Bounds()) {
				img.Set(px, py, c)
			}
		}
	}
}

var captchaGlyphs = map[byte][]string{
	'?': {"111", "001", "011", "000", "010"},
	'A': {"01110", "10001", "11111", "10001", "10001", "10001", "10001"},
	'B': {"11110", "10001", "10001", "11110", "10001", "10001", "11110"},
	'C': {"01111", "10000", "10000", "10000", "10000", "10000", "01111"},
	'D': {"11110", "10001", "10001", "10001", "10001", "10001", "11110"},
	'E': {"11111", "10000", "10000", "11110", "10000", "10000", "11111"},
	'F': {"11111", "10000", "10000", "11110", "10000", "10000", "10000"},
	'G': {"01111", "10000", "10000", "10011", "10001", "10001", "01110"},
	'H': {"10001", "10001", "10001", "11111", "10001", "10001", "10001"},
	'J': {"00111", "00010", "00010", "00010", "10010", "10010", "01100"},
	'K': {"10001", "10010", "10100", "11000", "10100", "10010", "10001"},
	'M': {"10001", "11011", "10101", "10101", "10001", "10001", "10001"},
	'N': {"10001", "11001", "10101", "10011", "10001", "10001", "10001"},
	'P': {"11110", "10001", "10001", "11110", "10000", "10000", "10000"},
	'Q': {"01110", "10001", "10001", "10001", "10101", "10010", "01101"},
	'R': {"11110", "10001", "10001", "11110", "10100", "10010", "10001"},
	'S': {"01111", "10000", "10000", "01110", "00001", "00001", "11110"},
	'T': {"11111", "00100", "00100", "00100", "00100", "00100", "00100"},
	'U': {"10001", "10001", "10001", "10001", "10001", "10001", "01110"},
	'V': {"10001", "10001", "10001", "10001", "10001", "01010", "00100"},
	'W': {"10001", "10001", "10001", "10101", "10101", "10101", "01010"},
	'X': {"10001", "10001", "01010", "00100", "01010", "10001", "10001"},
	'Y': {"10001", "10001", "01010", "00100", "00100", "00100", "00100"},
	'Z': {"11111", "00001", "00010", "00100", "01000", "10000", "11111"},
	'2': {"01110", "10001", "00001", "00010", "00100", "01000", "11111"},
	'3': {"11110", "00001", "00001", "01110", "00001", "00001", "11110"},
	'4': {"00010", "00110", "01010", "10010", "11111", "00010", "00010"},
	'5': {"11111", "10000", "10000", "11110", "00001", "00001", "11110"},
	'6': {"01110", "10000", "10000", "11110", "10001", "10001", "01110"},
	'7': {"11111", "00001", "00010", "00100", "01000", "01000", "01000"},
	'8': {"01110", "10001", "10001", "01110", "10001", "10001", "01110"},
	'9': {"01110", "10001", "10001", "01111", "00001", "00001", "01110"},
}
