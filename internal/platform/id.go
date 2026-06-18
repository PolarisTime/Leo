package platform

import (
	"sync"
	"time"
)

const (
	snowflakeEpoch        = int64(1704038400000)
	snowflakeSequenceMask = int64(4095)
)

type IDGenerator struct {
	machineID     int64
	mu            sync.Mutex
	lastTimestamp int64
	sequence      int64
}

func NewIDGenerator(machineID int64) *IDGenerator {
	if machineID < 0 || machineID > 1023 {
		machineID = 0
	}
	return &IDGenerator{machineID: machineID, lastTimestamp: -1}
}

func (g *IDGenerator) Next() int64 {
	g.mu.Lock()
	defer g.mu.Unlock()

	timestamp := time.Now().UnixMilli()
	if timestamp < g.lastTimestamp {
		timestamp = g.lastTimestamp
	}
	if timestamp == g.lastTimestamp {
		g.sequence = (g.sequence + 1) & snowflakeSequenceMask
		if g.sequence == 0 {
			for timestamp <= g.lastTimestamp {
				timestamp = time.Now().UnixMilli()
			}
		}
	} else {
		g.sequence = 0
	}
	g.lastTimestamp = timestamp
	return ((timestamp - snowflakeEpoch) << 22) | (g.machineID << 12) | g.sequence
}
